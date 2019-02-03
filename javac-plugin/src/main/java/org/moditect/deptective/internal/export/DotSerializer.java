/**
 *  Copyright 2019 The ModiTect authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.moditect.deptective.internal.export;

import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.moditect.deptective.internal.model.Component;
import org.moditect.deptective.internal.model.Component.ReadCounter;
import org.moditect.deptective.internal.model.PackagePattern;
import org.moditect.deptective.internal.model.ReadKind;

/**
 * Serializes models to GraphViz format ("DOT files").
 *
 * @author Gunnar Morling
 */
public class DotSerializer implements ModelSerializer {

    private final StringBuilder sb;
    private final SortedSet<String> allPackages;
    private final SortedMap<String, SortedSet<ComponentReference>> allowedReads;
    private final SortedMap<String, SortedSet<ComponentReference>> disallowedReads;
    private final SortedMap<String, SortedSet<ComponentReference>> cycleReads;
    private final SortedMap<String, SortedSet<ComponentReference>> unknownReads;

    public DotSerializer() {
        sb = new StringBuilder();
        sb.append("digraph \"package dependencies\"\n");
        sb.append("{\n");

        allPackages = new TreeSet<>();
        allowedReads = new TreeMap<>();
        disallowedReads = new TreeMap<>();
        cycleReads = new TreeMap<>();
        unknownReads = new TreeMap<>();
    }

    private static class ComponentReference implements Comparable<ComponentReference> {

        private final String referencedPackageName;
        private final int count;

        public ComponentReference(String referencedPackageName, int count) {
            this.referencedPackageName = referencedPackageName;
            this.count = count;
        }

        @Override
        public int compareTo(ComponentReference o) {
            return referencedPackageName.compareTo(o.referencedPackageName);
        }
    }

    @Override
    public void addComponent(Component component) {
        allPackages.add(component.getName());

        SortedSet<ComponentReference> allowed = new TreeSet<>();
        allowedReads.put(component.getName(), allowed);

        SortedSet<ComponentReference> disallowed = new TreeSet<>();
        disallowedReads.put(component.getName(), disallowed);

        SortedSet<ComponentReference> cycle = new TreeSet<>();
        cycleReads.put(component.getName(), cycle);

        SortedSet<ComponentReference> unknown = new TreeSet<>();
        unknownReads.put(component.getName(), unknown);

        for (Entry<String, ReadCounter> referencedPackage : component.getReads().entrySet()) {
            String referencedPackageName = referencedPackage.getKey();
            ComponentReference reference = new ComponentReference(
                    referencedPackageName, referencedPackage.getValue().getCount()
            );
            allPackages.add(referencedPackageName);

            if (referencedPackage.getValue().getReadKind() == ReadKind.ALLOWED) {
                allowed.add(reference);
            }
            else if (referencedPackage.getValue().getReadKind() == ReadKind.DISALLOWED) {
                disallowed.add(reference);
            }
            else if (referencedPackage.getValue().getReadKind() == ReadKind.CYCLE) {
                cycle.add(reference);
            }
            else {
                unknown.add(reference);
            }
        }
    }

    @Override
    public void addWhitelistedPackagePattern(PackagePattern pattern) {
    }

    @Override
    public String serialize() {
        for (String pakkage : allPackages) {
            sb.append("  \"").append(pakkage).append("\";").append(System.lineSeparator());
        }

        addSubGraph(sb, allowedReads, "Allowed", null, false);
        addSubGraph(sb, disallowedReads, "Disallowed", "red", true);
        addSubGraph(sb, cycleReads, "Cycle", "purple", true);
        addSubGraph(sb, unknownReads, "Unknown", "yellow", true);

        sb.append("}");

        return sb.toString();
    }

    private void addSubGraph(StringBuilder sb, SortedMap<String, SortedSet<ComponentReference>> readsOfKind,
            String kind,
            String color,
            boolean showCount) {

        StringBuilder subGraphBuilder = new StringBuilder();
        boolean atLeastOneEdge = false;

        subGraphBuilder.append("  subgraph " + kind + " {").append(System.lineSeparator());
        if (color != null) {
            subGraphBuilder.append("    edge [color=" + color + ", penwidth=2]").append(System.lineSeparator());
        }
        for (Entry<String, SortedSet<ComponentReference>> reads : readsOfKind.entrySet()) {
            for (ComponentReference read : reads.getValue()) {
                subGraphBuilder.append("    \"").append(reads.getKey()).append("\" -> \"")
                        .append(read.referencedPackageName).append("\"");
                if (showCount) {
                    subGraphBuilder.append(" [ label=\" " + read.count + "\" ]");
                }
                subGraphBuilder.append(";\n");
                atLeastOneEdge = true;
            }
        }

        subGraphBuilder.append("  }").append(System.lineSeparator());

        if (atLeastOneEdge) {
            sb.append(subGraphBuilder);
        }
    }
}
