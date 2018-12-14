package com.shaposhnyk

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.jooq.Record
import org.jooq.TableField

class FilteringFetcher(
    val delegate: DataFetcher<Iterable<Record>>,
    val filterKeys: List<TableField<out Record, *>>
) {

    fun get(environment: DataFetchingEnvironment, parent: Record?): Iterable<Record> {
        val sourceData = delegate.get(environment)
        val filterKeyValues = filterKeys.map { Pair(it, it.get(parent)) }.toList()

        return sourceData.filter { record ->
            filterKeyValues.asSequence()
                .all { p -> p.second == p.first.get(record) }
        }
    }

    companion object {
        fun of(
            delegate: DataFetcher<Iterable<Record>>,
            filterKeys: List<TableField<out Record, *>>
        ): FilteringFetcher {
            return FilteringFetcher(delegate, filterKeys);
        }
    }

}
