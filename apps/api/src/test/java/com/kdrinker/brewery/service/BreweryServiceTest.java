package com.kdrinker.brewery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdrinker.brewery.dto.BreweryRequest;
import com.kdrinker.brewery.dto.BreweryResponse;
import com.kdrinker.brewery.entity.Brewery;
import com.kdrinker.brewery.entity.BrewerySource;
import com.kdrinker.brewery.repository.BreweryRepository;
import com.kdrinker.common.BusinessException;
import com.kdrinker.common.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BreweryServiceTest {

	@Mock
	private BreweryRepository breweryRepository;

	private BreweryService service() {
		return new BreweryService(breweryRepository);
	}

	private static BreweryRequest request(String name, String handle) {
		return new BreweryRequest(name, "서울", null, null, handle, null);
	}

	@Test
	void 핸들은_골뱅이와_URL을_뗀다() {
		// given
		Brewery brewery = Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA);
		when(breweryRepository.findById(1L)).thenReturn(Optional.of(brewery));

		// when
		BreweryResponse response = service().update(1L, request("화요", "https://instagram.com/hwayo_official/"));

		// then
		assertThat(response.instagramHandle()).isEqualTo("hwayo_official");
		assertThat(response.source()).isEqualTo("MANUAL");
		assertThat(response.verified()).isTrue();
	}

	@Test
	void 잘못된_핸들은_400이다() {
		// given
		Brewery brewery = Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA);
		when(breweryRepository.findById(1L)).thenReturn(Optional.of(brewery));

		// when / then
		assertThatThrownBy(() -> service().update(1L, request("화요", "잘못된 핸들!")))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BREWERY_INVALID_INSTAGRAM));
	}

	@Test
	void 없는_양조장은_404다() {
		// given
		when(breweryRepository.findById(9L)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service().update(9L, request("화요", null)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 정규화한_이름이_비면_400이다() {
		// given
		Brewery brewery = Brewery.of("화요", "화요", BrewerySource.PUBLIC_DATA);

		// when / then
		assertThatThrownBy(() -> service().applyEdit(brewery, request("(주)", null)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
	}

	@Test
	void 생성하면_저장하고_MANUAL이다() {
		// given
		when(breweryRepository.save(any(Brewery.class))).thenAnswer(i -> i.getArgument(0));

		// when
		BreweryResponse response = service().create(request("새 양조장", "@new_brew"));

		// then
		assertThat(response.name()).isEqualTo("새 양조장");
		assertThat(response.instagramHandle()).isEqualTo("new_brew");
		assertThat(response.source()).isEqualTo("MANUAL");
	}

}
