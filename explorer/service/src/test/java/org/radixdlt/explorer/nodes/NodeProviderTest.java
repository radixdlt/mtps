package org.radixdlt.explorer.nodes;

import io.reactivex.Observer;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.radixdlt.explorer.nodes.model.NodeInfo;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.radixdlt.explorer.nodes.Utils.getMockClient;

public class NodeProviderTest {

    @Test
    public void when_node_finder_returns_plain_text_response__further_node_info_is_requested() {
        OkHttpClient mockClient = getMockClient(
                "192.168.0.1", "text/plain",
                "{\"shards\":{\"range\":{\"low\":1,\"high\":3}}}", "application/json");

        Observer<List<NodeInfo>> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(anyList());

        NodeProvider nodeProvider = new NodeProvider(mockClient, "https://192.168.0.0/nodes", 100L, 1f);
        nodeProvider.start();
        nodeProvider.getNodesObservable().subscribe(mockObserver);

        ArgumentCaptor<List<NodeInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockObserver, timeout(300L).times(1)).onNext(captor.capture());

        NodeInfo node = captor.getValue().get(0);
        assertThat(node.getAddress()).isEqualTo("192.168.0.1");
        assertThat(node.getLow()).isEqualTo(1);
        assertThat(node.getHigh()).isEqualTo(3);
    }

    @Test
    public void when_node_finder_returns_json_response__no_further_node_info_is_requested() {
        OkHttpClient mockClient = getMockClient(
                "{\"192.168.0.2\":{\"shards\":{\"range\":{\"low\":4,\"high\":6}}}}", "application/json",
                "{\"shards\":{\"range\":{\"low\":1,\"high\":3}}}", "application/json");

        Observer<List<NodeInfo>> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(anyList());

        NodeProvider nodeProvider = new NodeProvider(mockClient, "https://192.168.0.0/nodes", 100L, 1f);
        nodeProvider.start();
        nodeProvider.getNodesObservable().subscribe(mockObserver);

        ArgumentCaptor<List<NodeInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockObserver, timeout(300L).times(1)).onNext(captor.capture());

        NodeInfo node = captor.getValue().get(0);
        assertThat(node.getAddress()).isEqualTo("192.168.0.2");
        assertThat(node.getLow()).isEqualTo(4);
        assertThat(node.getHigh()).isEqualTo(6);
    }

    @Test
    public void when_nodes_have_overlapping_shards__then_overlapping_nodes_are_not_selected() {
        OkHttpClient mockClient = getMockClient(
                "192.168.0.3\n192.168.0.4\n192.168.0.5", "text/plain",
                "{\"shards\":{\"range\":{\"low\":1,\"high\":3}}}", "application/json",
                "{\"shards\":{\"range\":{\"low\":2,\"high\":5}}}", "application/json",
                "{\"shards\":{\"range\":{\"low\":4,\"high\":6}}}", "application/json");

        Observer<List<NodeInfo>> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(anyList());

        NodeProvider nodeProvider = new NodeProvider(mockClient, "https://192.168.0.0/nodes", 100L, 1f);
        nodeProvider.start();
        nodeProvider.getNodesObservable().subscribe(mockObserver);

        ArgumentCaptor<List<NodeInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockObserver, timeout(300L).times(1)).onNext(captor.capture());

        List<NodeInfo> nodes = captor.getValue();
        List<NodeInfo> selected = nodes.stream()
                .filter(NodeInfo::isSelected)
                .collect(Collectors.toList());

        long totalCount = nodes.size();
        long selectedCount = selected.size();
        assertThat(totalCount).isEqualTo(3L);
        assertThat(selectedCount).isEqualTo(2L);

        String address1 = selected.get(0).getAddress();
        String address2 = selected.get(1).getAddress();
        assertThat(address1).isNotEqualTo(address2);
        assertThat(address1).isIn("192.168.0.3", "192.168.0.5"); // Magic to handle non-ordered nature of JSON objects.
        assertThat(address2).isIn("192.168.0.3", "192.168.0.5");
    }

    @Test
    public void when_nodes_have_not_changed__no_second_data_emission_is_done() {
        OkHttpClient mockClient = getMockClient(
                "192.168.0.6\n192.168.0.7", "text/plain",
                "{\"shards\":{\"range\":{\"low\":1,\"high\":3}}}", "application/json",
                "{\"shards\":{\"range\":{\"low\":1,\"high\":3}}}", "application/json");

        Observer<List<NodeInfo>> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(anyList());

        NodeProvider nodeProvider = new NodeProvider(mockClient, "https://192.168.0.0/nodes", 100L, 1f);
        nodeProvider.start();
        nodeProvider.getNodesObservable().subscribe(mockObserver);
        verify(mockObserver, timeout(300L).times(1)).onNext(anyList());
    }

    @Test
    public void when_nodes_have_changed__a_second_data_emission_is_done() {
        OkHttpClient mockClient = getMockClient(
                "192.168.0.8", "text/plain",
                "{\"shards\":{\"range\":{\"low\":1,\"high\":2}}}", "application/json",
                "192.168.0.2", "text/plain",
                "{\"shards\":{\"range\":{\"low\":3,\"high\":4}}}", "application/json");

        Observer<List<NodeInfo>> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(anyList());

        NodeProvider nodeProvider = new NodeProvider(mockClient, "https:192.168.0.0/nodes", 100L, 1f);
        nodeProvider.start();
        nodeProvider.getNodesObservable().subscribe(mockObserver);
        verify(mockObserver, timeout(300L).times(2)).onNext(anyList());
    }

}
