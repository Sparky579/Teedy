create cached table T_USER_REGISTRATION (
  REG_ID_C varchar(36) not null,
  REG_USERNAME_C varchar(50) not null,
  REG_PASSWORD_C varchar(200) not null,
  REG_EMAIL_C varchar(100) not null,
  REG_CREATEDATE_D timestamp not null,
  REG_STATUS_C varchar(20) not null,
  primary key (REG_ID_C)
);

create index IDX_REG_USERNAME on T_USER_REGISTRATION (REG_USERNAME_C);
create index IDX_REG_STATUS on T_USER_REGISTRATION (REG_STATUS_C);

update T_CONFIG set CFG_VALUE_C = '32' where CFG_ID_C = 'DB_VERSION'; 