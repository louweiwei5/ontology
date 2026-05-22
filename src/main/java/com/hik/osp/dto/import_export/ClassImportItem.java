package com.hik.osp.dto.import_export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassImportItem {
    private String name;
    private String description;
    private String parentClass;
}
