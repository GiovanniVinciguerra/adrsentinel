package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator;

import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem.PackageDetail.PackageType;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorPackageType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del validatore custom legato all'annotazione {@link ValidatorPackageType}.
 * <p><b>Contesto Architetturale (Boundary Protection & Anti-Corruption):</b></p>
 * Questa classe agisce come un "guardiano" ai confini dell'applicazione (es. strato REST/Controller).
 * Il suo compito è intercettare le stringhe provenienti dall'esterno (JSON payload) e verificare 
 * che corrispondano esattamente a uno dei valori previsti dall'enumerazione interna di dominio 
 * {@link PackageType}, proteggendo la logica di business da input spuri.
 * <p><b>Gestione Controllata delle Eccezioni (Fail-Safe Enum Parsing):</b></p>
 * Un problema comune e critico nello sviluppo di API in Java è il crash del motore di 
 * deserializzazione quando tenta di mappare una stringa errata direttamente su un Enum. 
 * Ricevendo il dato come {@code String} e validandolo qui, il sistema assorbe internamente 
 * l'eccezione {@link IllegalArgumentException} lanciata nativamente da Java. 
 * Questo approccio converte un potenziale errore fatale di sistema in una gestione 
 * elegante e controllata del rifiuto (HTTP 400 Validation Error).
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorPackageType
 */
public class PackageTypeValidator implements ConstraintValidator<ValidatorPackageType, String> {
	/**
	 * Esegue la validazione effettiva sulla stringa di input rappresentante il tipo di imballaggio.
	 * <p><b>Flusso di Validazione (Pipeline di Sicurezza):</b></p>
	 * <ol>
	 * <li><i>Null-Safety:</i> Rifiuta immediatamente valori nulli. La presenza del dato testuale 
	 * è un requisito fondamentale per l'elaborazione del D.D.T.</li>
	 * <li><i>Type-Matching (Dictionary Check):</i> Tenta la conversione <i>case-sensitive</i> dalla stringa 
	 * al tipo enumerato tramite la funzione nativa {@code Enum.valueOf()}. Se la stringa non esiste 
	 * a dizionario, l'eccezione viene intercettata silenziando il crash e fallendo la validazione.</li>
	 * <li><i>Database Safeguard:</i> Come barriera di sicurezza strutturale finale, assicura che 
	 * la stringa non ecceda il limite standard dei campi VARCHAR (255 caratteri), 
	 * prevenendo anomalie in fase di persistenza.</li>
	 * </ol>
	 * @param value Il tipo di imballaggio in formato testuale (estratto dal payload).
	 * @param context Il contesto di validazione fornito da Hibernate Validator, utile per 
	 * costruire violazioni customizzate (non utilizzato in questo scope).
	 * @return {@code true} se la stringa corrisponde a un valore ammesso a dizionario e rispetta i vincoli fisici, 
	 * {@code false} in caso di valore nullo, disallineato rispetto all'Enum o eccessivamente lungo.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		try {
			Enum.valueOf(PackageType.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
