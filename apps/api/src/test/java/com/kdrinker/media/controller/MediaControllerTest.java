package com.kdrinker.media.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdrinker.common.GlobalExceptionHandler;
import com.kdrinker.media.service.PhotoService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MediaController.class)
@Import(GlobalExceptionHandler.class)
class MediaControllerTest {

	private static final String TOKEN = "Ab1_-".repeat(8) + "xyz";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PhotoService photoService;

	@Test
	void 있는_토큰이면_jpeg를_준다() throws Exception {
		// given
		byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
		when(photoService.readRendered(TOKEN)).thenReturn(Optional.of(jpeg));

		// when
		var result = mockMvc.perform(get("/api/v1/media/" + TOKEN + ".jpg"));

		// then
		result.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_JPEG))
				.andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
				.andExpect(content().bytes(jpeg));
	}

	@Test
	void 없는_토큰이나_형식이_틀린_토큰이면_404다() throws Exception {
		// given
		when(photoService.readRendered(TOKEN)).thenReturn(Optional.empty());

		// when / then
		mockMvc.perform(get("/api/v1/media/" + TOKEN + ".jpg")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/media/abc.jpg")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/media/..%2F..%2Fetc%2Fpasswd.jpg")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/media/" + "a".repeat(42) + ".jpg")).andExpect(status().isNotFound());
	}

}
