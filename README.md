# Vaultsync

Copyright 2023 Cogohi

**LICENSE:** MIT (See `LICENSE`)

## Introduction

Vaultsync is the spiritual successor to VaultSter.  It is used to transfer character files between NWN servervaults.  The current incarnation is a Spring Boot application.  Unlike its predecessor it does not provide an NWNX plugin.  The interface between the NWN server and the Vaultsync server is via a REST interface.  There's another REST interface between peers and the actual data transfer is via FTPS (FTP over SSL)

## Configuration

(Needs a better writeup)

Vaultsync is based on mutual x509 certificate authentication.  You and your peer need to share a common certificate authority.  At this time the author highly recommends that you DO NOT use a public CA.

Your peers (the servers you will be sending to and receiving from) must be defined in Vaultsync's application.yml.  Please see `sampleconfig/application.yml` for details.

## Dependencies

You will need a way for NWN to talk to a REST service.  Until a proper REST plugin is developed for NWNX:EE you can use [REST Glue for NWNX:EE Lua](https://github.com/Cogohi/REST-glue-for-NWNX-Lua) which relies on the NWNX:EE's Lua plugin.