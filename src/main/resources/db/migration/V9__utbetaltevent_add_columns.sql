alter table utbetaltevent ADD COLUMN utbetalingid UUID NULL;
alter table utbetaltevent ADD COLUMN utbetaling_fom DATE NULL;
alter table utbetaltevent ADD COLUMN utbetaling_tom DATE NULL;
