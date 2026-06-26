package com.archivenexus.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NexusApiSmokeTest {
    @Autowired
    MockMvc mvc;

    @Test
    void exposesFactorySimulatorAndRpaApis() throws Exception {
        mvc.perform(get("/api/factories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mvc.perform(get("/api/simulator/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factoryCount").value(3));

        mvc.perform(post("/api/simulator/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));

        mvc.perform(get("/api/rpa/tasks"))
                .andExpect(status().isOk());
    }
}
