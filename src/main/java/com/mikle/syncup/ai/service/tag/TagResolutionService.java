package com.mikle.syncup.ai.service.tag;

import com.mikle.syncup.ai.model.vo.TagResolutionResult;

import java.util.List;

public interface TagResolutionService {

    TagResolutionResult resolve(List<String> tagQueries);
}
