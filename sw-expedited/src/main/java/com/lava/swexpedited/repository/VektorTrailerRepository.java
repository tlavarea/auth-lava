package com.lava.swexpedited.repository;

import com.lava.swexpedited.vektor.VektorTrailerRow;
import java.util.List;
import java.util.Optional;

public interface VektorTrailerRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - same convention as
     * {@link SamsaraDriverRepository#replaceAll}.
     */
    void replaceAll(List<VektorTrailerRow> rows);

    List<VektorTrailerRow> findAll();

    Optional<VektorTrailerRow> findById(String id);
}
