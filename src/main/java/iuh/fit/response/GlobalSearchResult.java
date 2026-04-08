package iuh.fit.response;

import org.springframework.data.domain.Page;

import iuh.fit.document.DocumentDocument;
import iuh.fit.document.MessageDocument;
import iuh.fit.document.UserDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResult {
    private Page<SearchResult<MessageDocument>> messages;
    private Page<SearchResult<UserDocument>> users;
    private Page<SearchResult<DocumentDocument>> documents;
}
