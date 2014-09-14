package dex;

import java.util.LinkedHashSet;
import java.util.Set;

public class SetUtils {
	public static <T> Set<T> setDifference(Set<T> set1, Set<T> set2) {
		Set<T> s = new LinkedHashSet<T>(set1);
		s.removeAll(set2);
		return s;
	}

	public static <T> Set<T> setIntersection(Set<T> set1, Set<T> set2) {
		Set<T> s = new LinkedHashSet<T>(set1);
		s.retainAll(set2);
		return s;
	}
}
