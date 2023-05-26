/* Licensed under MIT 2023. */
package edu.kit.kastel.mcse.ardoco.docker;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import com.github.dockerjava.api.DockerClient;

@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class RemoteDockerManagerTest {
    private static final String REMOTE_TEST_IP = "141.3.52.97";
    private static final int REMOTE_TEST_PORT = 2375;
    private DockerManager dm;

    @Test
    void testRemoteHTTPServer() throws Exception {
        dm = new DockerManager(REMOTE_TEST_IP, REMOTE_TEST_PORT, "tests", true);
        Assertions.assertTrue(dm.getContainerIds().isEmpty());
        var containerInformation = dm.createContainerByImage("httpd:2.4", true, true);
        Assertions.assertNotNull(containerInformation);
        Assertions.assertNotNull(containerInformation.containerId());
        Assertions.assertFalse(containerInformation.containerId().isBlank());
        Assertions.assertEquals(1, dm.getContainerIds().size());
        Assertions.assertEquals(dm.getContainerIds().get(0), containerInformation.containerId());

        // Verify that the service runs ..
        URL url = new URL("http://" + REMOTE_TEST_IP + ":" + containerInformation.apiPort());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        InputStream is = con.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
        String line;
        while ((line = rd.readLine()) != null) {
            response.append(line);
            response.append('\n');
        }
        rd.close();
        var data = response.toString();
        Assertions.assertTrue(data.contains("It works!"));
    }

    @Test
    void testGPURemote() throws NoSuchFieldException, IllegalAccessException {
        dm = new DockerManager(REMOTE_TEST_IP, REMOTE_TEST_PORT, "tests", true);
        DockerAPI api = getDockerAPI();
        DockerClient docker = getClient(api);
        var noGPUId = api.createContainer("tests-no-gpu", "nvidia/cuda:10.2-base", new DockerPortBind(12345, 12345, false), false);
        Assertions.assertNotNull(noGPUId);
        var responseIdNoGPU = docker.execCreateCmd(noGPUId).withCmd("nvidia-smi").withAttachStdout(true).withAttachStderr(true).exec();
        docker.execStartCmd(responseIdNoGPU.getId()).exec(new ResultAwaitCallback()).awaitCompletion();
        var responseNoGPU = docker.inspectExecCmd(responseIdNoGPU.getId()).exec();
        dm.shutdownAll();

        var withGPU = api.createContainer("tests-with-gpu", "nvidia/cuda:10.2-base", new DockerPortBind(12345, 12345, false), true);
        Assertions.assertNotNull(withGPU);
        var responseIdWithGPU = docker.execCreateCmd(withGPU).withCmd("nvidia-smi").withAttachStdout(true).withAttachStderr(true).exec();
        docker.execStartCmd(responseIdWithGPU.getId()).exec(new ResultAwaitCallback()).awaitCompletion();
        var responseWithGPU = docker.inspectExecCmd(responseIdWithGPU.getId()).exec();
        dm.shutdownAll();

        // Command Not Executable 126
        Assertions.assertEquals(126L, responseNoGPU.getExitCodeLong());
        Assertions.assertEquals(0L, responseWithGPU.getExitCodeLong());

    }

    private DockerAPI getDockerAPI() throws NoSuchFieldException, IllegalAccessException {
        var field = DockerManager.class.getDeclaredField("dockerAPI");
        field.setAccessible(true);
        return (DockerAPI) field.get(this.dm);
    }

    private DockerClient getClient(DockerAPI api) throws NoSuchFieldException, IllegalAccessException {
        var field = DockerAPI.class.getDeclaredField("docker");
        field.setAccessible(true);
        return (DockerClient) field.get(api);
    }

    @AfterEach
    void tearDown() {
        if (dm == null)
            return;
        dm.shutdownAll();
        Assertions.assertTrue(dm.getContainerIds().isEmpty());
    }
}
