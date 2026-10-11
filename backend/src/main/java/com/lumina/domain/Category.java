package com.lumina.domain;

import com.lumina.domain.Enums.Domain;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@NoArgsConstructor
@Document("categories")
public class Category {
    @Id
    private String id;
    private String name;
    @Indexed(unique = true)
    private String slug;
    private Domain domain;
    private String parentId;
    private String description;
    private boolean active = true;
    private int order;
    private boolean seedData;
}
