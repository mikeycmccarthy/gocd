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

package com.thoughtworks.go.domain.valuestreammap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

public class NodeLevelMap {


    private HashMap<Integer,List<Node>> map = new HashMap<Integer, List<Node>>();

    public void add(Node node) {
        int level = node.getLevel();
        if (map.get(level) == null) {
            map.put(level, new ArrayList<Node>());
        }
        map.get(level).add(node);
    }


    public List<Node> get(int level) {
        return map.get(level);
    }

    public int lowestLevel() {
        return sortedLevelNumbers().first();
    }

    public int highestLevel() {
        return sortedLevelNumbers().last();
    }

    private TreeSet<Integer> sortedLevelNumbers() {
        return new TreeSet<Integer>(map.keySet());
    }

    public List<List<Node>> nodesAtEachLevel() {
        List<List<Node>> nodesAtEachLevel = new ArrayList<List<Node>>();
        TreeSet<Integer> sortedLevels = sortedLevelNumbers();
        for (Integer level : sortedLevels) {
            nodesAtEachLevel.add(map.get(level));
        }
        return nodesAtEachLevel;
    }
}