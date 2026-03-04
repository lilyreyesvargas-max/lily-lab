package com.lreyes.platform.modules.documents;

import com.lreyes.platform.modules.documents.dto.CreateDocumentRequest;
import com.lreyes.platform.modules.documents.dto.DocumentResponse;
import com.lreyes.platform.modules.documents.dto.UpdateDocumentRequest;
import com.lreyes.platform.shared.mapping.DefaultMapStructConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(config = DefaultMapStructConfig.class)
public interface DocumentMapper {

    DocumentResponse toResponse(Document entity);

    Document toEntity(CreateDocumentRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateDocumentRequest dto, @MappingTarget Document entity);

    List<DocumentResponse> toResponseList(List<Document> entities);
}
