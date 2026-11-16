package com.kdrinker.analysis.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfig {

	/**
	 * 키가 없으면 클라이언트 없이 만든다. 기동은 막지 않고 호출 시점에 ClaudeUnavailableException.
	 * 컨텍스트 종료 때 close()로 클라이언트를 닫는다.
	 */
	@Bean(destroyMethod = "close")
	public ClaudeCalls claudeCalls(ClaudeProperties props) {
		AnthropicClient client = null;
		if (props.hasApiKey()) {
			client = AnthropicOkHttpClient.builder()
					.apiKey(props.apiKey())
					.timeout(props.timeout())
					.maxRetries(props.maxRetries())
					.build();
		}
		return new ClaudeCalls(props, client);
	}
}
