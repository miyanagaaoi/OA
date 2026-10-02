package com.oa.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页结构（与 MyBatis-Plus 的 {@link IPage} 解耦，API 层只暴露本结构）。
 *
 * @param <T> 记录类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records = new ArrayList<>();
    private long total;
    private long page;
    private long size;
    private long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long page, long size) {
        this.records = records == null ? new ArrayList<>() : records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> from(IPage<T> source) {
        if (source == null) {
            return new PageResult<>(new ArrayList<>(), 0L, 1L, 0L);
        }
        return new PageResult<>(source.getRecords(), source.getTotal(), source.getCurrent(), source.getSize());
    }

    /** 分页查询返回实体分页、API 层再转换为 DTO 的场景。 */
    public static <S, T> PageResult<T> from(IPage<S> source, Function<S, T> mapper) {
        if (source == null) {
            return new PageResult<>(new ArrayList<>(), 0L, 1L, 0L);
        }
        List<T> mapped = new ArrayList<>();
        if (source.getRecords() != null) {
            for (S record : source.getRecords()) {
                mapped.add(mapper.apply(record));
            }
        }
        return new PageResult<>(mapped, source.getTotal(), source.getCurrent(), source.getSize());
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }
}
