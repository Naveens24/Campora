package in.ggv.campora.mobility.dto;

/**
 * Who is taking the student — revealed only once a request is matched.
 *
 * @param kind     "AUTO" or "COMPANION"
 * @param name     driver's or companion host's name
 * @param mobile   contact number
 * @param vehicle  vehicle description (auto rides) or null
 * @param detail   extra line, e.g. trip status or departure time
 */
public record MatchInfo(String kind, String name, String mobile, String vehicle, String detail) {
}
