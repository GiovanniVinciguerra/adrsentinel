package dev.vinciguerra.adrsentinel.web.dto.onunumber;

import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PackingGroup;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PhysicalState;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;

/**
 * Data Transfer Object (DTO) in sola lettura (Read-Only) per l'esposizione dei 
 * metadati logistici, chimici e normativi associati a una specifica materia pericolosa.
 * <p><b>Contesto di Dominio (Anagrafica Normativa ADR):</b></p>
 * Questo record modella la vera e propria "carta d'identità" di una sostanza secondo 
 * le direttive del trasporto internazionale ADR. Aggrega in un'unica struttura piatta 
 * tutte le informazioni necessarie per compilare una Lettera di Vettura, calcolare 
 * le esenzioni normative, determinare le etichette di pericolo (Placards) e pianificare 
 * il routing del veicolo.
 * <p><b>Scelta Architetturale (Java Record & Immutabilità):</b></p>
 * L'utilizzo del costrutto {@code record} (introdotto in Java 14+) garantisce nativamente 
 * l'immutabilità assoluta dell'oggetto e la sua natura <i>Thread-Safe</i>. Essendo un 
 * <i>Response DTO</i> (destinato alla serializzazione JSON verso il Frontend), l'oggetto 
 * viene istanziato dal Service/Mapper e viaggia verso il Controller senza alcun rischio 
 * di mutazione accidentale del suo stato durante il transito.
 * @param id L'identificatore tecnico primario (Surrogate Key) assegnato dal database. 
 * Utile lato client per operazioni di binding o come chiave in framework reattivi.
 * @param onuCode Il Numero ONU (UN Number) a 4 cifre (es. "1202" per Gasolio). Rappresenta 
 * la Business Key internazionale univoca della sostanza.
 * @param name La denominazione ufficiale di trasporto (Proper Shipping Name) della sostanza, 
 * testo legalmente vincolante da riportare sui documenti di viaggio.
 * @param physicalState Lo stato fisico della materia (es. Solido, Liquido, Gas). 
 * Parametro cardine per determinare le modalità di trasporto consentite  (in colli, in cisterna o alla rinfusa).
 * @param kemlerCode Il Numero di Identificazione del Pericolo (Hazard Identification Number), 
 * da esporre nella metà superiore dei pannelli arancioni sul mezzo (es. "33", "X88").
 * @param packingGroup Il Gruppo di Imballaggio (Packing Group - PG). Classifica il grado 
 * di pericolo chimico/fisico della sostanza: I (Alto), II (Medio), III (Basso).
 * @param tunnelRestriction Il codice di restrizione stradale (Tunnel Restriction Code, es. "D/E"). 
 * Impatta in modo determinante sugli algoritmi di routing, vietando il transito in specifiche gallerie autostradali.
 * @param transportCategory La Categoria di Trasporto (valore da 0 a 4). Moltiplicatore matematico 
 * essenziale per il calcolo delle esenzioni parziali (Regola del 1000 - ADR 1.1.3.6).
 * @param adrClass La Classe di Pericolo principale ADR (es. 3 per liquidi infiammabili, 8 per corrosivi). 
 * Guida le procedure di segregazione delle merci e le etichette da apporre sul carico.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record OnuNumberResponseDTO(Long id, String onuCode, String name, PhysicalState physicalState, String kemlerCode,
	PackingGroup packingGroup, TunnelRestriction tunnelRestriction, Integer transportCategory, AdrClass adrClass) {}
