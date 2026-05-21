package dev.vinciguerra.adrsentinel.web.dto.shipmentitem;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorPackingGroup;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorQuantity;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorUnitOfMeasure;

/**
 * Data Transfer Object (DTO) in ingresso (Request Payload) progettato per la creazione 
 * e l'inserimento di una nuova riga di carico (Shipment Item) nel sistema.
 * <p><b>Contesto Architetturale (Flat Payload & Business Keys):</b></p>
 * Questo record rappresenta lo stato dell'arte per la progettazione di API RESTful disaccoppiate. 
 * Evita l'<i>Anti-Pattern</i> dell'annidamento di oggetti complessi (Over-fetching) e non espone 
 * mai vulnerabili ID sequenziali del database (es. {@code shipment_id = 5}). Utilizza invece 
 * esclusivamente "Chiavi di Business" pubbliche e sicure: il Tracking Number per agganciare 
 * l'articolo alla spedizione padre, e la chiave composita (Codice ONU + Gruppo di Imballaggio) 
 * per recuperare l'esatta direttiva normativa ADR.
 * <p><b>Motore di Validazione Perimetrale (Unified Edge Validation):</b></p>
 * Il payload è blindato da un'architettura di validazione ibrida che agisce come scudo (Firewall Applicativo) 
 * prima dell'accesso al Service Layer:
 * <ul>
 * <li><b>Dominio Logistico ({@code @ValidatorUUID}, {@code @ValidatorOnuNumberCode}):</b> Garantisce 
 * che gli identificatori di sistema e le anagrafiche ONU rispettino i rigidi standard crittografici 
 * (formato UUID) e normativi (esattamente 4 cifre).</li>
 * <li><b>Grandezze Fisiche e Testi ({@code @ValidatorRequiredNumber}, {@code @ValidatorRequiredString}):</b> 
 * Assicura che le quantità siano reali (strettamente positive) e che i metadati testuali (Unità di 
 * misura, Gruppi di imballaggio) non causino <i>Payload Bloating</i> o <i>Data Truncation</i> nel database.</li>
 * </ul>
 * @param uuid L'identificatore univoco universale fornito dal client, spesso utilizzato 
 * come <i>Idempotency Key</i> per prevenire doppie creazioni in caso di retry di rete.
 * @param quantity La quantità fisica della materia trasportata (deve essere rigorosamente > 0).
 * @param unitOfMeasure L'unità di misura associata alla quantità (es. "KG", "L", "FUSTI").
 * @param shipmentTrackingNumber La Business Key primaria della spedizione "padre" a cui questo articolo appartiene.
 * @param onuNumberCode Il codice ONU a 4 cifre esatte, prima metà della chiave di lookup normativo.
 * @param packingGroup Il gruppo di imballaggio (es. "I", "II", "III"), seconda metà della chiave di lookup ADR.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentItemRequestDTO(@ValidatorUUID String uuid, @ValidatorQuantity Float quantity, @ValidatorUnitOfMeasure String unitOfMeasure,
	@ValidatorUUID String shipmentTrackingNumber, @ValidatorOnuNumberCode String onuNumberCode, @ValidatorPackingGroup String packingGroup) {}
