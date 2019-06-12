package org.radixdlt.explorer.helper.gson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.radixdlt.explorer.system.model.SystemInfo;

import java.io.IOException;

public class SystemInfoAdapter extends TypeAdapter<SystemInfo> {

    @Override
    public void write(JsonWriter out, SystemInfo value) {
        throw new UnsupportedOperationException("This is a read-only adapter");
    }

    @Override
    public SystemInfo read(JsonReader in) throws IOException {
        double stored = 0L;
        long storing = 0L;
        long anchor = 0L;
        long high = 0L;
        long low = 0L;
        long complex = 0L;
        long nonComplex = 0L;
        int depth = 0;

        in.beginObject();
        depth++;

        try {
            while (depth > 0) {
                if (!in.hasNext()) {
                    in.endObject();
                    depth--;
                    continue;
                }

                switch (in.nextName()) {
                    case "storedPerShard":
                        String value = in.nextString().replaceFirst(":str:", "");
                        double raw = Double.parseDouble(value);
                        stored = Math.round(raw);
                        break;
                    case "storingPerShard":
                        storing = in.nextLong();
                        break;
                    case "anchor":
                        anchor = in.nextLong();
                        break;
                    case "high":
                        high = in.nextLong();
                        break;
                    case "low":
                        low = in.nextLong();
                        break;
                    case "complex":
                        complex = in.nextLong();
                        break;
                    case "nonComplex":
                        nonComplex = in.nextLong();
                        break;
                    case "ledger":  // intentional fallthrough
                    case "shards":  // intentional fallthrough
                    case "range":   // intentional fallthrough
                    case "sync":
                        in.beginObject();
                        depth++;
                        break;
                    default:
                        in.skipValue();
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new SystemInfo(storing, stored, anchor, high, low, complex, nonComplex);
    }

}
