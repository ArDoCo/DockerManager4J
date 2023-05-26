/* Licensed under MIT 2022-2023. */
package edu.kit.kastel.mcse.ardoco.docker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.DeviceRequest;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

public final class DockerAPI {

    private static final Logger logger = LoggerFactory.getLogger(DockerAPI.class);
    private final DockerClient docker;
    private final boolean remote;

    public DockerAPI() {
        this.remote = false;
        DockerClient dockerInstance = null;
        try {
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            DockerHttpClient dhc = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
            dockerInstance = DockerClientImpl.getInstance(config, dhc);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        this.docker = dockerInstance;
        this.checkDockerExistence();
    }

    /**
     * Create a docker API for a remote docker instance.
     *
     * @param remoteIp   the ip of the remote docker host
     * @param remotePort the port that is used by the docker service
     */
    public DockerAPI(String remoteIp, int remotePort) {
        this.remote = true;
        DockerClient dockerInstance = null;
        try {
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost("tcp://" + remoteIp + ":" + remotePort).build();
            DockerHttpClient dhc = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
            dockerInstance = DockerClientImpl.getInstance(config, dhc);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        this.docker = dockerInstance;
        this.checkDockerExistence();
    }

    public List<DockerImage> listImagesCmd() {
        try {
            var images = docker.listImagesCmd().withShowAll(true).exec();

            return images.stream() //
                    .filter(image -> image.getRepoTags() != null) //
                    .map(image -> new DockerImage(image.getParentId(), image.getRepoTags()[0], null))
                    .filter(it -> !it.isNone())
                    .toList();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    public boolean pullImageCmd(String image) {
        try {
            docker.pullImageCmd(image).start().awaitCompletion();
            return true;
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    public List<DockerContainer> listContainersCmd(boolean showAll) {
        List<Container> containers = new ArrayList<>();
        try {
            containers = docker.listContainersCmd().withShowAll(showAll).exec();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return containers.stream()
                .map(container -> new DockerContainer(container.getId(), container.getImage(), container.getStatus(), container.getNames()[0]))
                .toList();
    }

    public DockerImage inspectImageCmd(String image) {
        try {
            var imageInspect = docker.inspectImageCmd(image).exec();
            return new DockerImage(//
                    Objects.requireNonNull(imageInspect.getRepoTags()).get(0), //
                    imageInspect.getRepoTags().get(0), //
                    Arrays.stream(Objects.requireNonNull(imageInspect.getConfig()).getExposedPorts()).map(ExposedPort::getPort).toList() //
            );
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public String createContainer(String name, String image, DockerPortBind dpb) {
        return createContainer(name, image, dpb, false);
    }

    public String createContainer(String name, String image, DockerPortBind dpb, boolean useGPU) {
        if (!dpb.valid()) {
            logger.error("DockerPortBind is invalid!");
            throw new IllegalArgumentException("Invalid Docker Port Binding");
        }

        var binding = (dpb.wildcard() ? "0.0.0.0:" : "127.0.0.1:") + dpb.hostPort() + ":" + dpb.containerPort();

        try (var command = docker.createContainerCmd(image).withTty(true).withAttachStdout(true).withAttachStderr(true)) {
            var host = HostConfig.newHostConfig().withPortBindings(PortBinding.parse(binding));
            if (useGPU)
                host = host.withDeviceRequests(List.of(new DeviceRequest() //
                        .withCount(-1) //
                        .withCapabilities(List.of(List.of("gpu")))));
            var container = command.withName(name).withHostConfig(host).exec();
            docker.startContainerCmd(container.getId()).exec();
            return container.getId();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public boolean killContainerCmd(String id) {
        try {
            docker.killContainerCmd(id).exec();
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    public boolean removeContainerCmd(String id) {
        try {
            docker.removeContainerCmd(id).exec();
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    private void checkDockerExistence() {
        if (docker == null)
            throw new IllegalArgumentException("Could not connect to Docker");
        var version = docker.versionCmd().exec();
        logger.info("Connected to Docker: {}", version);
    }

    public boolean isRemote() {
        return remote;
    }
}
