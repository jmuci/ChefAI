package com.tenmilelabs.chefai.data

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.domain.model.Tag
import com.tenmilelabs.chefai.domain.repository.TagsRepository
import com.tenmilelabs.chefai.testData.testTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class FakeTagsRepository @Inject constructor() : TagsRepository {

    override fun observeAll(): Flow<List<Tag>> {
        return flowOf(testTags.toDomain())
    }
}
