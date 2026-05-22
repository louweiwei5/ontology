package com.hik.osp.dto.import_export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassExportItem {
    private String name;
    private String description;
    private String parentClass;
}
