package dev.vinciguerra.adrsentinel.web.dto.vehicle;

import java.util.Set;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleApproval;

/**
 * Data Transfer Object (DTO) in uscita (Response Payload) che modella la classificazione 
 * tecnica e le omologazioni normative di un veicolo destinato al trasporto merci pericolose.
 * <p><b>Contesto Architetturale (Immutabilità e Contract Stability):</b></p>
 * Sfruttando la natura intrinsecamente immutabile dei {@code record} Java, questo oggetto 
 * funge da "Value Object" per il layer di presentazione. Assicura che i metadati di 
 * classificazione del mezzo, una volta estratti dal database, rimangano inalterabili 
 * durante tutto il processo di serializzazione. L'adozione di tipi testuali (String) 
 * per gli attributi garantisce un "API Contract" resiliente, disaccoppiando il client 
 * dalle evoluzioni interne degli enumeratori lato backend.
 *
 * <p><b>Contesto di Dominio (Normativa ADR):</b></p>
 * Questo DTO trasporta i due parametri legali vincolanti per la circolazione dei mezzi ADR:
 * <ul>
 * <li><b>Tipo Veicolo:</b> Definisce l'allestimento tecnico di sicurezza (es. FL per liquidi 
 * infiammabili, AT per altre tipologie, EX per esplosivi).</li>
 * <li><b>Tipo Carico:</b> Specifica la modalità fisica di trasporto per cui il mezzo è 
 * omologato (es. Cisterna, Rinfusa, Colli).</li>
 * </ul>
 * L'integrità di questi dati è il prerequisito fondamentale per il funzionamento dei motori 
 * di calcolo della compatibilità e per la generazione dei documenti di viaggio legali.
 * @param vehicleType La codifica testuale della categoria tecnica ADR (es. "TANKER", "CURTAINSIDE").
 * @param loadType La descrizione testuale della tipologia di carico supportata (es. "SOLID", "LIQUID").
 * @param vehicleApprovals Certificati adr presenti per il veicolo in questione, può essere null nel qual caso 
 * verrà inserito un empty set che nel dominio significa nessuna certificazione adr.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record VehicleCategoryResponseDTO(String vehicleType, String loadType, Set<VehicleApproval> vehicleApprovals) {
	/**
	 * Factory Method statico per la conversione (Mapping) di un'entità di dominio 
	 * {@link VehicleCategory} nel suo corrispondente Data Transfer Object in uscita 
	 * {@link VehicleCategoryResponseDTO}.
	 * <p><b>Contesto Architetturale (Omologazione Flotta ADR):</b></p>
	 * Nel trasporto di merci pericolose, la categoria tecnica del veicolo (es. FL, OX, AT, EX/II, EX/III) 
	 * e la tipologia di carico supportata (es. Cisterna, Rinfusa, Colli) sono requisiti legali vincolanti 
	 * per determinare la compatibilità con una specifica spedizione (Numero ONU). Questo metodo agisce 
	 * come traduttore isolante, estraendo questi metadati critici dal modello relazionale (Hibernate) 
	 * e impacchettandoli in una struttura dati leggera ed esposta in modo sicuro al client.
	 * <p><b>Design Pattern e Strategie di Serializzazione:</b></p>
	 * <ul>
	 * <li><b>Sicurezza e Robustezza (Guard Clause):</b> L'implementazione inizia con un controllo 
	 * difensivo ({@code if(entity == null)}), rendendo il metodo intrinsecamente <i>Null-Safe</i>. 
	 * Questo è fondamentale poiché i metadati di categoria potrebbero essere opzionali o mancanti in 
	 * determinati scenari di lookup del veicolo, prevenendo così {@code NullPointerException} a runtime.</li>
	 * <li><b>Type Flattening (Disaccoppiamento Enum):</b> Entrambi i parametri di classificazione 
	 * (Tipo di Veicolo e Tipo di Carico) vengono esplicitamente convertiti nella loro controparte testuale 
	 * tramite l'invocazione di {@code .name()}. Questa pratica disaccoppia il payload JSON generato 
	 * dalle classi enumeratore (Enum) interne del backend, garantendo un "API Contract" stabile, 
	 * prevedibile e agnostico rispetto alla tecnologia del client (es. Angular, React, app mobili).</li>
	 * </ul>
	 * @param entity L'istanza dell'entità JPA recuperata dal database, rappresentante i 
	 * metadati di classificazione tecnica del veicolo. Ammette valori {@code null}.
	 * @return Una nuova istanza immutabile (Record) di {@link VehicleCategoryResponseDTO} pronta 
	 * per la serializzazione HTTP, oppure {@code null} se l'input fornito era assente.
	 */
	public static VehicleCategoryResponseDTO fromEntity(VehicleCategory entity) {
		if(entity == null)
			return null;
		
		return new VehicleCategoryResponseDTO(
			entity.getVehicleType().name(),
			entity.getLoadType().name(),
			entity.getVehicleApprovals()
		);
	}
}
