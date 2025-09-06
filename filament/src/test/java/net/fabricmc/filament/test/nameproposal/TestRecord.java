package net.fabricmc.filament.test.nameproposal;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

public record TestRecord(int a, String another, Optional<LongStream> data, List<Double> aBitOfLongName) {
	@Override
	public String toString() {
		return "I replaced this so you have to scan other methods";
	}
}
