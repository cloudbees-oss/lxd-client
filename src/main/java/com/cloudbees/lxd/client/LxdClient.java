package com.cloudbees.lxd.client;

import com.cloudbees.lxd.client.api.ContainerAction;
import com.cloudbees.lxd.client.api.ContainerInfo;
import com.cloudbees.lxd.client.api.ContainerState;
import com.cloudbees.lxd.client.api.Device;
import com.cloudbees.lxd.client.api.ImageAliasesEntry;
import com.cloudbees.lxd.client.api.ImageInfo;
import com.cloudbees.lxd.client.api.LxdResponse;
import com.cloudbees.lxd.client.api.Operation;
import com.cloudbees.lxd.client.api.ResponseType;
import com.cloudbees.lxd.client.api.ServerState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import okhttp3.MediaType;
import okhttp3.RequestBody;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

/**
 * Asynchronous LXD client based on RxJava.
 */
public class LxdClient implements AutoCloseable {

    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    protected static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
        .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);

    private static final String RECURSION_SUFFIX = "?recursion=1";


    protected final RxOkHttpClientWrapper rxClient;

    public LxdClient() {
        this(Config.localAccessConfig());
    }

    public LxdClient(Config config) {
        this.rxClient = new RxOkHttpClientWrapper(config, new LxdResponseParser.Factory(JSON_MAPPER));
    }

    @Override
    public void close() throws Exception {
        rxClient.close();
    }

    /**
     * @return Server configuration and environment information
     */
    public Single<ServerState> serverState() {
        return rxClient.get("1.0").build()
            .flatMap(rp -> rp.parseSyncSingle(new TypeReference<LxdResponse<ServerState>>() {}));
    }

    /**
     * @return List of existing containers
     */
    public Single<List<ContainerInfo>> containers() {
        return rxClient.get("1.0/containers" + RECURSION_SUFFIX).build()
            .flatMap(rp -> rp.parseSyncSingle(new TypeReference<LxdResponse<List<ContainerInfo>>>() {}));
    }

    public Container container(String name) {
        return new Container(name);
    }

    public class Container {
        final String containerName;

        /**
         * Returns an API to interact with container containerName
         * @param containerName the name of the container. Should be 64 chars max, ASCII, no slash, no colon and no comma
         */
        Container(String containerName) {
            this.containerName = containerName;
        }

        /**
         * Change the container state
         * @param action State change action
         * @param timeout A timeout after which the state change is considered as failed
         * @param force Force the state change (currently only valid for stop and restart where it means killing the container)
         * @param stateful Whether to store or restore runtime state before stopping or starting (only valid for stop and start, defaults to false)
         * @return
         */
        public Completable action(ContainerAction action, int timeout, boolean force, boolean stateful) {
            Map<String, Object> body = new HashMap<>();
            body.put("action", action.getValue());
            body.put("timeout", timeout);
            body.put("force", force);

            switch (action) {
                case Start:
                case Stop:
                    body.put("stateful", stateful);
            }

            return rxClient.put(format("1.0/containers/%s/state", containerName), json(body)).build()
                    .flatMap(rp -> Single.just(rp.parseOperation(ResponseType.ASYNC, 202)))
                .flatMapCompletable(o -> waitForCompletion(o));
        }

        public Completable delete() {
            return rxClient.delete(format("1.0/containers/%s", containerName)).build()
                .flatMap(rp -> Single.just(rp.parseOperation(ResponseType.ASYNC, 202)))
                .flatMapCompletable(o -> waitForCompletion(o));
        }

        public Completable deleteSnapshot(String snapshotName) {
            return rxClient.delete(format("1.0/containers/%s/snapshots/%s", containerName, snapshotName)).build()
                .flatMap(rp -> Single.just(rp.parseOperation(ResponseType.ASYNC, 202)))
                .flatMapCompletable(o -> waitForCompletion(o));
        }

        public Maybe<ContainerInfo> info() {
            return rxClient.get(format("1.0/containers/%s", containerName)).build()
                .flatMapMaybe(rp -> rp.parseSyncMaybe(new TypeReference<LxdResponse<ContainerInfo>>() {}));
        }

        /**
         * Create a new container
         * @param imgremote either null for the local LXD daemon or one of remote name defined in {@link Config#remotesURL}
         * @param image
         * @param profiles
         * @param config Config override
         * @param devices Optional list of devices the container should have
         * @param ephem Whether to destroy the container on shutdown
         * @return
         */
        public Completable init(String imgremote, String image, List<String> profiles, Map<String, String> config, List<Device> devices, boolean ephem) {

            Map<String, String> source = new HashMap<>();
            source.put("type", "image");
            if (imgremote != null) {
                source.put("server", rxClient.getConfig().getRemotesURL().get(imgremote));
                source.put("protocol", "simplestreams");
                // source.put("certificate", ); <= fetch the cert?
                source.put("fingerprint", image);
            } else {
                throw new IllegalArgumentException();
                /*
                ImageAliasesEntry alias = imageGetAlias(image);
                String fingerprint = alias != null ? alias.getTarget() : image;
                ImageInfo imageInfo = imageInfo(fingerprint);
                if (imageInfo == null) {
                    throw new LxdClientException("Unable to find image locally");
                }
                source.put("fingerprint", fingerprint);
                */
            }

            Map<String, Object> body = new HashMap<>();
            body.put("source", source);
            body.put("name", containerName);

            if (profiles != null && !profiles.isEmpty()) {
                body.put("profiles", profiles);
            }
            if (config != null && !config.isEmpty()) {
                body.put("config", config);
            }
            if (devices != null && !devices.isEmpty()) {
                body.put("devices", devices);
            }
            if (ephem) {
                body.put("ephem", ephem);
            }

            return rxClient.post(format("1.0/containers", containerName), json(body)).build()
                .flatMap(rp -> Single.just(rp.parseOperation(ResponseType.ASYNC, 202)))
                .flatMapCompletable(o -> waitForCompletion(o));
        }

        /**
         * Starts the container. Does nothing if the container is already started.
         * Before issuing the start request, container state is checked and start request is issued only if
         * state is Stopped
         *
         * @return
         */
        public Completable start() {
            return action(ContainerAction.Start, 0, false, false);
        }

        public Maybe<ContainerState> state() {
            return rxClient.get(format("1.0/containers/%s", containerName)).build()
               .flatMapMaybe(rp -> rp.parseSyncMaybe(new TypeReference<LxdResponse<ContainerState>>() {}));
        }

        public Completable stop(int timeout, boolean force, boolean stateful) {
            return action(ContainerAction.Stop, timeout, force, stateful);
        }

        public Completable filePush(String targetPath, int gid, int uid, String mode, RequestBody body) {
            return rxClient
                .post(urlBuilder -> urlBuilder
                    .addPathSegment("1.0/containers").addPathSegment(containerName).addPathSegment("files")
                    .addEncodedQueryParameter("path", targetPath),
                    body)
                .build(requestBuilder -> requestBuilder
                    .addHeader("X-LXD-type", "file")
                    .addHeader("X-LXD-mode", mode)
                    .addHeader("X-LXD-uid", String.valueOf(uid))
                    .addHeader("X-LXD-gid", String.valueOf(gid)))
                .flatMapCompletable(rp -> rp.parse(new TypeReference<LxdResponse<Void>>() {}, ResponseType.SYNC, 200) != null ?
                    Completable.complete() : Completable.error(new LxdClientException("")));
        }

        public Completable filePush(String targetPath, int gid, int uid, String mode, File file) {
            return filePush(targetPath, gid, uid, mode, RequestBody.create(MediaType.parse("application/octet-stream"), file));
        }
    }

    public Single<List<ImageInfo>> images() {
        return rxClient.get("1.0/images" + RECURSION_SUFFIX).build()
            .flatMap(rp -> rp.parseSyncSingle(new TypeReference<LxdResponse<List<ImageInfo>>>(){}));
    }

    public Image image(String imageFingerprint) {
        return new Image(imageFingerprint);
    }

    public class Image {
        final String imageFingerprint;

        Image(String imageFingerprint) {
            this.imageFingerprint = imageFingerprint;
        }

        public Maybe<ImageInfo> info() {
            return rxClient.get(format("1.0/images/%s", imageFingerprint)).build()
                .flatMapMaybe(rp -> rp.parseSyncMaybe(new TypeReference<LxdResponse<ImageInfo>>(){}));
        }

        public Completable delete() {
            return rxClient.delete(format("1.0/images/%s", imageFingerprint)).build()
                .flatMap(rp -> Single.just(rp.parseOperation(ResponseType.ASYNC, 202)))
                .flatMapCompletable(o -> waitForCompletion(o));
        }
    }

    public Maybe<ImageAliasesEntry> alias(String aliasName) {
        return rxClient.get(format("1.0/images/aliases/%s", aliasName)).build()
            .flatMapMaybe(rp -> rp.parseSyncMaybe(new TypeReference<LxdResponse<ImageAliasesEntry>>(){}));
    }

    /**
     * Polls LXD for operation completion
     * @param operationResponse
     * @return a stream of Operations
     */
    public Completable waitForCompletion(LxdResponse<Operation> operationResponse) {
        /*
           As explained in https://www.stgraber.org/2016/04/18/lxd-api-direct-interaction/
          "data about past operations disappears 5 seconds after they’re done."

          https://medium.com/@v.danylo/server-polling-and-retrying-failed-operations-with-retrofit-and-rxjava-8bcc7e641a5a#.9ji4311wi
         */
        return rxClient.get(format("%s/wait?timeout=5", operationResponse.getOperationUrl())).build()
            .flatMapObservable(rp -> Observable.just(rp.parseOperation(ResponseType.SYNC, 200).getData()))
            .repeat()
            .takeUntil(operation -> operation.getStatusCode() != Operation.Status.Running)
            .lastOrError()
            .flatMapCompletable(operation -> operation.getStatusCode() == Operation.Status.Success ? Completable.complete() : Completable.error(new LxdClientException("Failed to complete")));
    }

    protected RequestBody json(Object resource) {
        try {
            return RequestBody.create(MEDIA_TYPE_JSON, JSON_MAPPER.writeValueAsString(resource));
        } catch (JsonProcessingException e) {
            throw new LxdClientException(e);
        }
    }
}
