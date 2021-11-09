package ru.mail.polis.service.gasparyansokrat;

import one.nio.http.HttpClient;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import ru.mail.polis.lsm.DAO;
import ru.mail.polis.lsm.Record;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClusterService {

    private final ReplicationService replicationService;
    private String selfNode;
    private final ConsistentHash clusterNodes;
    private final Set<String> topology;

    ClusterService(final DAO dao, final Set<String> topology, final ServiceConfig servConf) {
        this.clusterNodes = new ConsistentHashImpl(topology, servConf.clusterIntervals);
        this.topology = topology;
        Map<String, HttpClient> clusterServers = buildTopology(servConf.port, this.topology);
        this.replicationService = new ReplicationService(dao, selfNode, clusterServers);
    }

    private Map<String, HttpClient> buildTopology(final int port, final Set<String> topology) {
        Map<String, HttpClient> clusterServers = new HashMap<>();
        final String sport = String.valueOf(port);
        for (final String node : topology) {
            if (node.contains(sport)) {
                this.selfNode = node;
            } else {
                clusterServers.put(node, new HttpClient(new ConnectionString(node)));
            }
        }
        return clusterServers;
    }

    public void stop() {
        replicationService.stop();
    }

    public Response handleRequest(final Request request, final Map<String, String> params) throws IOException {
        if (params == null) {
            return badRequest();
        }
        final int ack = Integer.parseInt(params.get("ack"));
        final int from = Integer.parseInt(params.get("from"));
        if (!validParameter(ack, from)) {
            return badRequest();
        }
        if (request.getMethod() == Request.METHOD_PUT) {
            addTimeStamp(request);
        }

        final List<String> nodes = clusterNodes.getNodes(params.get("id"), from);

        return replicationService.handleRequest(request, params, nodes);
    }

    private void addTimeStamp(Request request) {
        Timestamp time = new Timestamp(System.currentTimeMillis());
        Record record = Record.of(Record.DummyBuffer, ByteBuffer.wrap(request.getBody()), time);
        request.setBody(record.getRawBytes());
    }

    public int quorumCompute() {
        final float quorumMajority = 0.66f;
        return Math.round(getClusterSize() * quorumMajority);
    }

    public int getClusterSize() {
        return topology.size();
    }

    public Response internalRequest(final Request request, final String id) throws IOException {
        if (id.isEmpty()) {
            return badRequest();
        }
        final String host = request.getHost();
        boolean validHost = false;
        for (final String node : topology) {
            if (node.contains(host)) {
                validHost = true;
                break;
            }
        }
        if (!validHost) {
            return badGateway();
        }
        return replicationService.directRequest(id, request);
    }

    private Response badRequest() {
        return new Response(Response.BAD_REQUEST, Response.EMPTY);
    }

    private Response badGateway() {
        return new Response(Response.BAD_GATEWAY, Response.EMPTY);
    }

    private boolean validParameter(final int ack, final int from) {
        return from <= getClusterSize() && ack > 0 && ack <= from;
    }
}
