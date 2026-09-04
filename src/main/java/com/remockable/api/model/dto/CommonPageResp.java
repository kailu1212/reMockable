package com.remockable.api.model.dto;

import java.util.Collections;
import java.util.List;
import lombok.Data;
import org.springframework.data.domain.Page;

/**
 * 列表資料的統一回應包裝。
 *
 * <p>本專案的列表都很小（Mock Set 最多 3 組、題目每題型最多 20 題、
 * 回答每題最多 100 筆），所以多半用 {@link #resp(List)} 一次回完。
 * 分頁版本保留給之後可能出現的長列表。
 */
@Data
public class CommonPageResp<DATA> {

    private long timestamp = System.currentTimeMillis();
    private Integer size;
    private int page;
    private int totalPages;
    private long total;
    private List<DATA> data;

    public static <DATA> CommonPageResp<DATA> resp(Page<DATA> pageData) {
        CommonPageResp<DATA> resp = new CommonPageResp<>();
        if (pageData != null) {
            resp.setTotalPages(pageData.getTotalPages());
            resp.setPage(pageData.getNumber());
            resp.setSize(pageData.getSize());
            resp.setTotal(pageData.getTotalElements());
            resp.setData(pageData.getContent());
        }
        return resp;
    }

    /** 不分頁的完整列表。 */
    public static <DATA> CommonPageResp<DATA> resp(List<DATA> listData) {
        return resp(listData, 0, null);
    }

    public static <DATA> CommonPageResp<DATA> resp(List<DATA> listData, Integer page, Integer size) {
        CommonPageResp<DATA> resp = new CommonPageResp<>();
        if (listData != null) {
            resp.setTotalPages(1);
            resp.setPage(page == null ? 0 : page);
            resp.setSize(size);
            resp.setTotal(listData.size());
            resp.setData(listData);
        }
        return resp;
    }

    /** 已經握有全部資料時，在記憶體內切頁。 */
    public static <DATA> CommonPageResp<DATA> respSubList(List<DATA> listData, Integer page, Integer size) {
        CommonPageResp<DATA> resp = new CommonPageResp<>();
        if (listData == null || size == null || size <= 0) {
            resp.setData(Collections.emptyList());
            return resp;
        }

        int totalItems = listData.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalItems);

        resp.setTotalPages(totalPages);
        resp.setPage(page);
        resp.setSize(size);
        resp.setTotal(totalItems);
        resp.setData(page < 0 || fromIndex >= totalItems ? Collections.emptyList() : listData.subList(fromIndex, toIndex));
        return resp;
    }
}
