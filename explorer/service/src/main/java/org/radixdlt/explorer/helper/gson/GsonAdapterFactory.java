package org.radixdlt.explorer.helper.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import org.radixdlt.explorer.helper.gson.adapter.NodeInfoAdapter;
import org.radixdlt.explorer.helper.gson.adapter.SystemInfoAdapter;
import org.radixdlt.explorer.nodes.model.NodeInfo;
import org.radixdlt.explorer.system.model.SystemInfo;

public class GsonAdapterFactory implements TypeAdapterFactory {

    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Class<T> objectType) {
        if (NodeInfo[].class.isAssignableFrom(objectType)) {
            return (TypeAdapter<T>) new NodeInfoAdapter();
        } else if (SystemInfo.class.isAssignableFrom(objectType)) {
            return (TypeAdapter<T>) new SystemInfoAdapter();
        } else {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<?> rawType = type.getRawType();
        return (TypeAdapter<T>) create(rawType);
    }

}
