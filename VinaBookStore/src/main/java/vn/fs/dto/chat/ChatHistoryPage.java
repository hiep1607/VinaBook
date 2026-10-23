package vn.fs.dto.chat;

import java.util.List;
import java.util.Map;

public record ChatHistoryPage(List<Map<String,Object>> messages,Long nextBefore,boolean hasMore) {}
