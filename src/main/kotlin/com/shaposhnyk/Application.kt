package com.shaposhnyk

import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import org.jooq.DSLContext
import org.jooq.Record
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
            .fieldOf { fBuilder ->
                fBuilder.withName("displayName")
                    .withSourceColumns(Tables.IDENTIFICATION.FIRST_NAME, Tables.IDENTIFICATION.LAST_NAME)
                    .extractFrom { displayName(it) }
            }
            .field("yearOfBirth", Tables.IDENTIFICATION.YEAR_OF_BIRTH)
            .field(Tables.IDENTIFICATION.NATURE)

        val nat = EntityBuilder.newBuilder(Tables.NATIONALITY) { dsl(jdbcTemplate) }
            .field("countryRef", Tables.NATIONALITY.COUNTRYCODE)

        val people = EntityBuilder.newBuilder(Tables.PERSON) { dsl(jdbcTemplate) }
            .field("ref", Tables.PERSON.PERSONREF)
            .field(Tables.PERSON.CORRELATIONREF)
            .relOneToMany(nat)
            .relOneToMany(ident);

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

    private fun displayName(it: Record?): String {
        return "M. " + Tables.IDENTIFICATION.FIRST_NAME.get(it) +
                " " + Tables.IDENTIFICATION.LAST_NAME.get(it)?.toUpperCase()
    }

    private fun dsl(jdbcTemplate: JdbcTemplate): DSLContext {
        val connection = jdbcTemplate?.dataSource
            ?.connection
        return DSL.using(connection, SQLDialect.H2)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
