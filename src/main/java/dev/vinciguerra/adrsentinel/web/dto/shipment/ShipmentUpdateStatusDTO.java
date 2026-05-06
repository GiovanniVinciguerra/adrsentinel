package dev.vinciguerra.adrsentinel.web.dto.shipment;

import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorShipmentStatus;

/**
 * Data Transfer Object (DTO) in ingresso (Request Payload) ultra-specializzato, 
 * dedicato esclusivamente all'avanzamento della Macchina a Stati (State Machine) logistica.
 * <p><b>Contesto Architetturale (Separation of Concerns e Granularità):</b></p>
 * Nel dominio Enterprise, la transizione di stato di una spedizione (es. da {@code CREATA} 
 * a {@code IN_TRANSITO}) non è una banale mutazione di stringa, ma un evento di business critico 
 * che spesso innesca logiche complesse (es. notifiche push, webhooks, fatturazione). 
 * Isolando questo singolo parametro in un DTO dedicato, l'architettura ottiene tre grandi vantaggi:
 * <ul>
 * <li><b>Sicurezza (Prevenzione Mass Assignment):</b> Un client malevolo non può in alcun modo 
 * iniettare o alterare surrettiziamente altri campi (es. origini, destinazioni) sfruttando 
 * l'endpoint di avanzamento logistico.</li>
 * <li><b>Autorizzazione Mirata:</b> Esporre un payload così snello permette di agganciarlo 
 * a un endpoint specifico (es. {@code PUT /status/{tracking}}) che può essere protetto 
 * con permessi ad-hoc (es. l'autista può aggiornare lo stato dal palmare, ma non può 
 * cambiare gli indirizzi di consegna).</li>
 * <li><b>API Contract Cristallino:</b> Il contratto Swagger/OpenAPI generato mostrerà ai 
 * clienti esattamente qual è l'unico dato richiesto per completare questa transazione.</li>
 * </ul>
 * <p><b>Edge Validation (Scudo Perimetrale):</b></p>
 * Il campo è difeso dalla validazione custom {@code @ValidatorShipmentStatus}, che agisce 
 * come "Anti-Corruption Layer" respingendo payload nulli o stringhe malevole/giganti prima 
 * ancora di tentare la decodifica nell'Enum interno.
 * @param status La rappresentazione testuale del nuovo stato del ciclo di vita ADR. 
 * Deve coincidere in modo rigoroso (case-sensitive) con le costanti dichiarate 
 * nel dominio logico (es. l'Enum {@code ShipmentStatus}) per superare la 
 * successiva fase di conversione nel Service.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentUpdateStatusDTO(@ValidatorShipmentStatus String status) {}
