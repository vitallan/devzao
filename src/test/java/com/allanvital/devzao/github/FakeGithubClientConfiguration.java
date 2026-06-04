package com.allanvital.devzao.github;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@TestConfiguration
public class FakeGithubClientConfiguration {

    @Bean
    @Primary
    public FakeGitHubClient fakeGitHubClient() {
        return new FakeGitHubClient();
    }

}
