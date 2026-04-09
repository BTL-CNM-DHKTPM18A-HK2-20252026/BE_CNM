package iuh.fit.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult<T> {
    private T document;
    private Map<String, List<String>> highlights;
    private String friendshipStatus;
}
