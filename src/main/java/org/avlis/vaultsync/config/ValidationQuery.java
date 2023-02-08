package org.avlis.vaultsync.config;

import jakarta.validation.constraints.Pattern;

import lombok.*;

import org.springframework.validation.annotation.Validated;

/**
 * Database lookups to validate that the player and character
 * is allowed on the server.
 * 
 * The query is an SQL statement in the SQL dialect of the connected database
 * Note: Everything before the FROM will be replaced with SELECT count(*)
 * If the count > 0 the transfer request is rejected.
 * 
 * The following substitutions will be made:
 * :world The sending world
 * :cdkey The player's cdkey
 * :login The player's login
 * :name The character's name
 * 
 * Recommended validations
 * BLOCKED: Not accepting any transfers or transfers from a specific world.
 * BANNED, JAILED
 * WRONG WORLD: Character transferring from a world they should not have been able to be on.
 *   This will depend heavily on topology.
 * 
 * Examples:
 * BLOCKED (all)
 * FROM servervars WHERE name = "enableVaultSync" and value = "false"
 * 
 * BLOCKED (world based)
 * FROM portals WHERE world = :world and enabled = "false"
 * 
 * JAILED
 * FROM player 
 * WHERE    status = "jailed" AND
 *          now() < releaseDate AND
 *          ( player = :login OR cdkey = :cdkey )
 */

@Setter
@Getter
@Validated
public class ValidationQuery {
    @Pattern(regexp = "^[Ff][Rr][Oo][Mm] .*", message = "Query must start with the FROM clause")
    String query;
    Integer rejectionCode;
    String rejectionReason;
}
