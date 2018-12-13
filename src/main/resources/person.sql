CREATE TABLE person (
  personRef       VARCHAR2(50)     NOT NULL PRIMARY KEY,
  correlationRef  VARCHAR2(50)     NOT NULL
);

CREATE TABLE identification (
  id              NUMBER(7)     NOT NULL PRIMARY KEY,
  personRef       VARCHAR2(50)  NOT NULL,
  last_name       VARCHAR2(50)  NOT NULL,
  first_name      VARCHAR2(50),
  nature          VARCHAR2(50),
  date_of_birth   DATE,
  year_of_birth   NUMBER(7),

  CONSTRAINT fk_ident_person_ref     FOREIGN KEY (personRef)   REFERENCES person(personRef)
);

CREATE TABLE nationality (
  id              NUMBER(7)     NOT NULL PRIMARY KEY,
  personRef       VARCHAR2(50)  NOT NULL,
  correlationRef  VARCHAR2(50)  NOT NULL,
  countryCode     VARCHAR2(50)  NOT NULL,
  begin_date       VARCHAR2(10),
  end_date         VARCHAR2(10),
  is_active      NUMBER(1),

  CONSTRAINT fk_contacts_person_ref     FOREIGN KEY (personRef)   REFERENCES person(personRef)
);