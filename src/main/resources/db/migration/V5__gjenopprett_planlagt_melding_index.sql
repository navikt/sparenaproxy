create index concurrently planlagt_melding_fnrsd_idx on planlagt_melding(fnr, startdato);
create index concurrently planlagt_melding_send_idx on planlagt_melding(sendes, sendt, avbrutt);
