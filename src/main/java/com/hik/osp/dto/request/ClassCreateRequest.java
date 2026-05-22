package com.hik.osp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassCreateRequest {
    @NotBlank(message = "Class name is required")
    @Size(max = 255)
    private String name;

    private String description;

    private String parentClassName;
}
