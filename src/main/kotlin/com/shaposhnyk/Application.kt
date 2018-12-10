package com.shaposhnyk

import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.generated.Tables
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate


@SpringBootApplication
class Application {
    @Bean
    @Autowired
    fun schema(jdbcTemplate: JdbcTemplate): GraphQLSchema {
        val ident = EntityBuilder.newBuilder(Tables.IDENTIFICATION) { dsl(jdbcTemplate) }
            .field("firstName", Tables.IDENTIFICATION.FIRST_NAME)
            .field("lastName", Tables.IDENTIFICATION.LAST_NAME) { it?.toUpperCase() }
            .field("yearOfBirth", Tables.IDENTIFICATION.YEAR_OF_BIRTH)
            .field(Tables.IDENTIFICATION.NATURE)

        val nat = EntityBuilder.newBuilder(Tables.NATIONALITY) { dsl(jdbcTemplate) }
            .field("countryRef", Tables.NATIONALITY.COUNTRYCODE)

        val people = EntityBuilder.newBuilder(Tables.PERSON) { dsl(jdbcTemplate) }
            .field("ref", Tables.PERSON.PERSONREF)
            .field(Tables.PERSON.CORRELATIONREF)

        return GraphQLSchema
            .newSchema()
            .query(
                GraphQLObjectType.newObject()
                    .name("queries")
                    .field {
                        it.name("findPersons")
                            .type(people.buildObjectListType())
                            .dataFetcher(people.buildFetcher())
                    }
            )
            .build();
    }

    private fun dsl(jdbcTemplate: JdbcTemplate): DSLContext {
        return DSL.using(jdbcTemplate.dataSource.connection, SQLDialect.H2)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
