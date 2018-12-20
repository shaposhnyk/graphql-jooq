DROP TABLE IF EXISTS person;
CREATE TABLE person (
  personRef       VARCHAR2(50)     NOT NULL PRIMARY KEY,
  correlationRef  VARCHAR2(50)
);

DROP TABLE IF EXISTS identification ;
CREATE TABLE identification (
  id              NUMBER(7)     NOT NULL PRIMARY KEY,
  personRef       VARCHAR2(50),
  last_name       VARCHAR2(50),
  first_name      VARCHAR2(50),
  nature          VARCHAR2(50),
  date_of_birth   DATE,
  year_of_birth   NUMBER(7),

  CONSTRAINT fk_ident_person_ref     FOREIGN KEY (personRef)   REFERENCES person(personRef)
);

DROP TABLE IF EXISTS nationality ;
CREATE TABLE nationality (
  id              NUMBER(7)     NOT NULL PRIMARY KEY,
  personRef       VARCHAR2(50)  NOT NULL,
  countryCode     VARCHAR2(50)  NOT NULL,
  correlationRef  VARCHAR2(50),
  begin_date       VARCHAR2(10),
  end_date         VARCHAR2(10),
  is_active      NUMBER(1),

  CONSTRAINT fk_nats_person_ref     FOREIGN KEY (personRef)   REFERENCES person(personRef)
);

DROP TABLE IF EXISTS relations;
CREATE TABLE relations (
  parentRef       VARCHAR2(50)     NOT NULL,
  type            VARCHAR2(50),
  childRef        VARCHAR2(50)     NOT NULL,
  CONSTRAINT fk_rels_parent_ref     FOREIGN KEY (parentRef)   REFERENCES person(personRef),
  CONSTRAINT fk_rels_child_ref     FOREIGN KEY (childRef)   REFERENCES person(personRef)
);