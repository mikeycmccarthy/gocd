/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.server.security;

import com.thoughtworks.go.domain.User;
import com.thoughtworks.go.i18n.Localizable;
import com.thoughtworks.go.i18n.LocalizedMessage;
import com.thoughtworks.go.plugin.access.authentication.AuthenticationExtension;
import com.thoughtworks.go.plugin.access.authentication.AuthenticationPluginRegistry;
import com.thoughtworks.go.presentation.UserSearchModel;
import com.thoughtworks.go.presentation.UserSourceType;
import com.thoughtworks.go.server.service.GoConfigService;
import com.thoughtworks.go.server.service.result.HttpLocalizedOperationResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class UserSearchServiceTest {
    @Mock
    private LdapUserSearch ldapUserSearch;
    @Mock
    private PasswordFileUserSearch passwordFileUserSearch;
    @Mock
    private AuthenticationPluginRegistry authenticationPluginRegistry;
    @Mock
    private AuthenticationExtension authenticationExtension;
    @Mock
    private GoConfigService goConfigService;
    private UserSearchService userSearchService;

    @Before
    public void setUp() {
        initMocks(this);

        when(goConfigService.isLdapConfigured()).thenReturn(true);

        when(authenticationPluginRegistry.getPluginsThatSupportsUserSearch()).thenReturn(new HashSet<String>());

        userSearchService = new UserSearchService(ldapUserSearch, passwordFileUserSearch, authenticationPluginRegistry, authenticationExtension, goConfigService);
    }

    @Test
    public void shouldSearchForUsers() throws Exception {
        User foo = new User("foo", new ArrayList<String>(), "foo@cruise.com", false);
        User bar = new User("bar-foo", new ArrayList<String>(), "bar@go.com", true);
        when(ldapUserSearch.search("foo")).thenReturn(Arrays.asList(foo, bar));
        List<UserSearchModel> models = userSearchService.search("foo", new HttpLocalizedOperationResult());
        assertThat(models, is(Arrays.asList(new UserSearchModel(foo, UserSourceType.LDAP), new UserSearchModel(bar, UserSourceType.LDAP))));
    }

    @Test
    public void shouldAddPluginSearchResults() throws Exception {
        String searchTerm = "foo";

        User foo = new User("foo", new ArrayList<String>(), "foo@cruise.com", false);
        User bar = new User("bar-foo", new ArrayList<String>(), "bar@go.com", true);
        when(ldapUserSearch.search(searchTerm)).thenReturn(Arrays.asList(foo, bar));

        List<String> pluginIds = Arrays.asList("plugin-id-1", "plugin-id-2");
        when(authenticationPluginRegistry.getPluginsThatSupportsUserSearch()).thenReturn(new LinkedHashSet<String>(pluginIds));
        when(authenticationExtension.searchUser("plugin-id-1", searchTerm)).thenReturn(Arrays.asList(getPluginUser(1)));
        when(authenticationExtension.searchUser("plugin-id-2", searchTerm)).thenReturn(Arrays.asList(getPluginUser(2), getPluginUser(3)));
        when(authenticationExtension.searchUser("plugin-id-3", searchTerm)).thenReturn(new ArrayList<com.thoughtworks.go.plugin.access.authentication.model.User>());

        List<UserSearchModel> models = userSearchService.search(searchTerm, new HttpLocalizedOperationResult());

        assertThat(models, is(Arrays.asList(new UserSearchModel(foo, UserSourceType.LDAP), new UserSearchModel(bar, UserSourceType.LDAP), new UserSearchModel(getUser(1), UserSourceType.PLUGIN), new UserSearchModel(getUser(2), UserSourceType.PLUGIN), new UserSearchModel(getUser(3), UserSourceType.PLUGIN))));
    }

    @Test
    public void shouldReturnWarningMessageWhenPasswordSearchFails() throws Exception {
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(goConfigService.isPasswordFileConfigured()).thenReturn(true);
        when(passwordFileUserSearch.search("foo")).thenThrow(new RuntimeException("Password file not found"));
        when(ldapUserSearch.search("foo")).thenReturn(new ArrayList<User>());
        List<UserSearchModel> models = userSearchService.search("foo", result);
        assertThat(models.size(), is(0));
        assertThat(result.localizable(), is((Localizable) LocalizedMessage.string("PASSWORD_SEARCH_FAILED")));
    }

    @Test
    public void shouldNotReturnWarningMessageWhenPasswordFileIsNotConfigured() throws Exception {
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(goConfigService.isPasswordFileConfigured()).thenReturn(false);
        when(ldapUserSearch.search("foo")).thenReturn(Arrays.asList(new User("foo")));
        List<UserSearchModel> models = userSearchService.search("foo", result);
        assertThat(models.size(), is(1));
        assertThat(result.hasMessage(), is(false));
    }

    @Test
    public void search_shouldNotAttemptLdapSearchIfLdapIsNotConfigured() throws Exception {
        User foo = new User("foo", new ArrayList<String>(), "foo@cruise.com", false);
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(goConfigService.isLdapConfigured()).thenReturn(false);
        when(passwordFileUserSearch.search("foo")).thenReturn(Arrays.asList(foo));
        userSearchService.search("foo", result);
        verifyNoMoreInteractions(ldapUserSearch);
    }

    @Test
    public void search_shouldNotAttemptSearchThroughPluginIfNoPluginSupportsUserSearch() throws Exception {
        when(goConfigService.isPasswordFileConfigured()).thenReturn(false);
        when(goConfigService.isLdapConfigured()).thenReturn(false);
        when(authenticationPluginRegistry.getPluginsThatSupportsUserSearch()).thenReturn(new HashSet<String>());

        userSearchService.search("foo", new HttpLocalizedOperationResult());

        verify(authenticationExtension, never()).searchUser(any(String.class), eq("foo"));
    }

    @Test
    public void shouldReturnWarningMessageWhenLdapSearchFails() throws Exception {
        User foo = new User("foo", new ArrayList<String>(), "foo@cruise.com", false);
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();

        when(goConfigService.isPasswordFileConfigured()).thenReturn(true);
        when(ldapUserSearch.search("foo")).thenThrow(new RuntimeException("Ldap Error"));
        when(passwordFileUserSearch.search("foo")).thenReturn(Arrays.asList(foo));
        List<UserSearchModel> models = userSearchService.search("foo", result);
        assertThat(models, is(Arrays.asList(new UserSearchModel(foo, UserSourceType.PASSWORD_FILE))));
        assertThat(result.localizable(), is((Localizable) LocalizedMessage.string("LDAP_ERROR")));
    }

    @Test
    public void shouldReturnWarningMessageWhenSearchReturnsNoResults() throws Exception {
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(ldapUserSearch.search("foo")).thenReturn(new ArrayList());
        when(passwordFileUserSearch.search("foo")).thenReturn(new ArrayList());
        userSearchService.search("foo", result);
        assertThat(result.localizable(), is((Localizable) LocalizedMessage.string("NO_SEARCH_RESULTS_ERROR")));
    }

    @Test
    public void shouldFailSearchIfBothLdapAndPasswordFileFail() throws Exception {
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        when(goConfigService.isPasswordFileConfigured()).thenReturn(true);
        when(ldapUserSearch.search("foo")).thenThrow(new RuntimeException("Ldap Error"));
        when(passwordFileUserSearch.search("foo")).thenThrow(new IOException("Password file not found"));
        userSearchService.search("foo", result);
        assertThat(result.localizable(), is((Localizable) LocalizedMessage.string("USER_SEARCH_FAILED")));
    }

    @Test
    public void shouldNotInvokeSearchWhenUserSearchTextIsTooSmall() throws Exception {
        String smallSearchText = "f";
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        userSearchService.search(smallSearchText, result);
        verify(ldapUserSearch, never()).search(smallSearchText);
        verify(passwordFileUserSearch, never()).search(smallSearchText);
    }

    @Test
    public void shouldNotInvokeSearchWhenUserSearchTextIsTooSmallAfterTrimming() throws Exception {
        String smallSearchText = "a ";
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();
        userSearchService.search(smallSearchText, result);

        verify(ldapUserSearch, never()).search(smallSearchText);
        verify(passwordFileUserSearch, never()).search(smallSearchText);
    }

    @Test
    public void shouldLimitSearchResultsAndWarnTheUser() throws Exception {
        HttpLocalizedOperationResult result = new HttpLocalizedOperationResult();

        User foo = new User("fooUser", "Foo User", "foo@user.com");
        User bar = new User("barUser", "boo User", "boo@user.com");

        when(ldapUserSearch.search("foo")).thenThrow(new LdapUserSearch.NotAllResultsShownException(Arrays.asList(foo, bar)));
        when(passwordFileUserSearch.search("foo")).thenReturn(new ArrayList<User>());

        List<UserSearchModel> models = userSearchService.search("foo", result);
        assertThat(models.size(), is(2));
        assertThat(result.localizable(), is((Localizable) LocalizedMessage.string("NOT_ALL_RESULTS_SHOWN")));
    }

    private User getUser(Integer userId) {
        return new User("username-" + userId, "display-name-" + userId, "test" + userId + "@test.com");
    }

    private com.thoughtworks.go.plugin.access.authentication.model.User getPluginUser(Integer userId) {
        return new com.thoughtworks.go.plugin.access.authentication.model.User("username-" + userId, "display-name-" + userId, "test" + userId + "@test.com");
    }
}
