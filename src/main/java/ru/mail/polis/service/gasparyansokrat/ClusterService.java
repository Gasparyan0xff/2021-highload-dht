package ru.mail.polis.service.gasparyansokrat;

import one.nio.http.Request;
import one.nio.http.Response;
import ru.mail.polis.lsm.DAO;
import ru.mail.polis.lsm.Record;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ClusterService {

    private final ReplicationService replicationService;
    private final ConsistentHash clusterNodes;
    //private final ExecutorService executorService;
    private final int topologySize;
    private final int quorumCluster;

    private static final float QUORUM_MAJORITY = 0.66f;
    public static final String BAD_REPLICAS = "504 Not Enough Replicas";

    ClusterService(final DAO dao, final Set<String> topology, final ServiceConfig servConf) {
        this.clusterNodes = new ConsistentHashImpl(topology);
        this.topologySize = topology.size();
        //this.executorService = Executors.newFixedThreadPool(servConf.poolSize);
        Map<String, HttpClient> clusterServers = buildTopology(servConf, topology);
        this.replicationService = new ReplicationService(dao, servConf.fullAddress, clusterServers);
        this.quorumCluster = Math.round(QUORUM_MAJORITY * topologySize);
    }

    private Map<String, HttpClient> buildTopology(final ServiceConfig servConf, final Set<String> topology) {
        Map<String, HttpClient> clusterServers = new HashMap<>();
        for (final String node : topology) {
            if (!node.equals(servConf.fullAddress)) {
                clusterServers.put(node,
                        HttpClient.newBuilder()
                                .build());
            }
        }
        return clusterServers;
    }

    public Response handleRequest(final Request request, final Map<String, String> params) throws IOException {
        if (params.get("id").isEmpty()) {
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

        long start = System.currentTimeMillis();
        Response resp = replicationService.handleRequest(request, ack, params.get("id"), nodes);
        long end = System.currentTimeMillis();
        //System.out.println("Elapsed times ms: " + (end - start));
        return resp;
    }

    public void stop() {
        /*
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Error! Await termination stop Cluster service...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }*/
    }

    private void addTimeStamp(Request request) {
        Timestamp time = new Timestamp(System.currentTimeMillis());
        Record record = Record.of(Record.DUMMY, ByteBuffer.wrap(request.getBody()), time);
        request.setBody(record.getRawBytes());
    }

    public int getClusterSize() {
        return topologySize;
    }

    public int getQuorumCluster() {
        return quorumCluster;
    }

    public Response internalRequest(final Request request, final String id) throws IOException {
        if (id.isEmpty()) {
            return badRequest();
        }
        final String host = request.getHost();
        boolean validHost = false;
        final Set<String> topology = replicationService.getTopology();
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

    public static CompletableFuture<Response> asyncBadMethod() {
        return CompletableFuture.supplyAsync(() ->
                new Response(Response.METHOD_NOT_ALLOWED, ServiceImpl.BAD_REQUEST)
        );
    }

    private boolean validParameter(final int ack, final int from) {
        return from <= getClusterSize() && ack > 0 && ack <= from;
    }
}
