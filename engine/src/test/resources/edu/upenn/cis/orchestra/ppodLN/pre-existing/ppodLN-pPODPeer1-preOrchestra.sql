create schema PPOD1;


create table PPOD1.OTU
(
OTU_ID INTEGER NOT NULL,
LABEL VARCHAR(255) NOT NULL,
OBJ_VERSION INTEGER,
PPOD_VERSION INTEGER,
PRIMARY KEY (OTU_ID)
);

create table PPOD1.OTU_L
(
OTU_ID INTEGER NOT NULL,
LABEL VARCHAR(255) NOT NULL,
OBJ_VERSION INTEGER,
PPOD_VERSION INTEGER,
PRIMARY KEY (OTU_ID)
);

create table PPOD1.OTU_R
(
OTU_ID INTEGER NOT NULL,
LABEL VARCHAR(255) NOT NULL,
OBJ_VERSION INTEGER,
PPOD_VERSION INTEGER,
PRIMARY KEY (OTU_ID)
);