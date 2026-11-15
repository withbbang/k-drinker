package com.kdrinker.analysis.brewery;

import java.util.Optional;

/** DB에 없는 양조장을 바깥(웹)에서 찾는다. 출처로 뒷받침되지 않으면 비어 있다. */
public interface BreweryResearcher {

	Optional<ResearchedBrewery> research(String breweryName, String drinkName);
}
