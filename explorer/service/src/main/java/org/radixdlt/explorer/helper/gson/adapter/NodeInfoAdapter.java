package org.radixdlt.explorer.helper.gson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.radixdlt.explorer.nodes.model.NodeInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class NodeInfoAdapter extends TypeAdapter<NodeInfo[]> {
    private static final Pattern PATTERN = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    @Override
    public void write(JsonWriter out, NodeInfo[] value) {
        throw new UnsupportedOperationException("This is a read-only adapter");
    }

    @Override
    public NodeInfo[] read(JsonReader in) throws IOException {
        ArrayList<NodeInfo> nodes = new ArrayList<>();
        String node = "";
        long anchor = 0L;
        long high = 0L;
        long low = 0L;
        int depth = 0;

        in.beginObject();
        depth++;

        while (depth > 0) {
            if (!in.hasNext()) {
                in.endObject();
                depth--;

                if (depth == 1) {
                    nodes.add(new NodeInfo(node, anchor, high, low));
                }
                continue;
            }

            String name = in.nextName();
            if (PATTERN.matcher(name).matches()) {
                node = name;
                in.beginObject();
                depth++;
                continue;
            }

            switch (name) {
                case "anchor":
                    anchor = in.nextLong();
                    break;
                case "high":
                    high = in.nextLong();
                    break;
                case "low":
                    low = in.nextLong();
                    break;
                case "shards": // intentional fallthrough
                case "range":
                    in.beginObject();
                    depth++;
                    break;
                default:
                    in.skipValue();
                    break;
            }
        }

        return nodes.toArray(new NodeInfo[0]);
    }

}
