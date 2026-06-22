package dev.vinciguerra.adrsentinel.web.annotation.driver.validator;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorPhoneNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione {@code ValidatorPhoneNumber}.
 * <p>
 * Questa classe analizza una stringa in ingresso per determinare se rappresenta 
 * un numero di telefono cellulare valido, delegando la complessa logica di risoluzione 
 * dei prefissi e dei formati internazionali alla libreria <code>libphonenumber</code> di Google.
 * </p>
 * <p><b>Strategia di Validazione e Sicurezza:</b></p>
 * <ul>
 * <li><b>Fail-Fast:</b> Il metodo rigetta immediatamente valori nulli, vuoti o che superano 
 * i 21 caratteri di lunghezza. Questo limite previene attacchi di tipo DoS (Denial of Service) 
 * che mirano a saturare la CPU costringendo il parser a elaborare stringhe insensatamente lunghe.</li>
 * <li><b>Ottimizzazione (Thread-Safety):</b> L'istanza di {@link PhoneNumberUtil} è caricata in 
 * una costante <code>static final</code>. Essendo l'inizializzazione di questa classe computazionalmente 
 * pesante (carica i metadati globali in memoria) ed essendo la libreria thread-safe, questo approccio 
 * garantisce alte prestazioni in scenari ad elevata concorrenza.</li>
 * <li><b>Fallback Regionale:</b> Qualora il numero non sia fornito in formato internazionale (es. senza il prefisso '+'), 
 * il sistema tenta la risoluzione forzando la regione di default a <code>"IT"</code> (Italia).</li>
 * <li><b>Filtro Tipologico:</b> I numeri sintatticamente validi ma appartenenti a reti fisse pure 
 * vengono scartati. Sono ammessi solo numeri di tipo <code>MOBILE</code> o <code>FIXED_LINE_OR_MOBILE</code>.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorPhoneNumber
 */
public class PhoneNumberValidator implements ConstraintValidator<ValidatorPhoneNumber, String> {
	/**
	 * Istanza Singleton e thread-safe del parser di numeri di telefono. 
	 * Caricata staticamente per ottimizzare le performance ad ogni ciclo di validazione.
	 */
	private static final PhoneNumberUtil PHONE_NUM_UTIL = PhoneNumberUtil.getInstance();
	
	/**
	 * Verifica la validità della stringa fornita rispetto alle regole del numero di cellulare.
	 * @param value   la stringa contenente il numero di telefono da validare.
	 * @param context il contesto in cui viene valutato il vincolo (può essere usato per 
	 * sovrascrivere il messaggio di errore di default, se necessario).
	 * @return <code>true</code> se la stringa rappresenta un numero di cellulare valido; 
	 * <code>false</code> se è nulla, blank, troppo lunga, malformata o relativa a un numero fisso.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		if(value.length() > 21)
			return false;
		try {
			PhoneNumber number = PHONE_NUM_UTIL.parse(value, "IT");
			if(!PHONE_NUM_UTIL.isValidNumber(number))
				return false;
			PhoneNumberType type = PHONE_NUM_UTIL.getNumberType(number);
			/* Il numero è un Cellulare e non un Fisso */
			return type == PhoneNumberType.MOBILE || type == PhoneNumberType.FIXED_LINE_OR_MOBILE;
		} catch(Exception error) {
			return false;
		}
	}
}
