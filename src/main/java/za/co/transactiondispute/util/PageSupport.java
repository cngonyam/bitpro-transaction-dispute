package za.co.transactiondispute.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public final class PageSupport {

    private PageSupport() {
    }

    public static <T> Page<T> fromList(List<T> content, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(content, Pageable.unpaged(), content.size());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), content.size());
        List<T> pageContent = start >= content.size() ? List.of() : content.subList(start, end);
        return new PageImpl<>(pageContent, pageable, content.size());
    }
}
