package gov.usgs.cida.wqp.webservice.SimpleStation;

import gov.cida.cdat.control.SCManager;
import gov.cida.cdat.control.Time;
import gov.cida.cdat.io.Closer;
import gov.cida.cdat.io.TransformOutputStream;
import gov.cida.cdat.io.container.SimpleStreamContainer;
import gov.cida.cdat.io.container.StreamContainer;
import gov.cida.cdat.transform.IXmlMapping;
import gov.cida.cdat.transform.MapToJsonTransformer;
import gov.cida.cdat.transform.MapToXlsxTransformer;
import gov.cida.cdat.transform.MapToXmlTransformer;
import gov.cida.cdat.transform.Transformer;
import gov.usgs.cida.wqp.dao.ICountDao;
import gov.usgs.cida.wqp.dao.IDao;
import gov.usgs.cida.wqp.dao.IStreamingDao;
import gov.usgs.cida.wqp.parameter.IParameterHandler;
import gov.usgs.cida.wqp.parameter.ParameterMap;
import gov.usgs.cida.wqp.parameter.Parameters;
import gov.usgs.cida.wqp.service.ILogService;
import gov.usgs.cida.wqp.util.HttpConstants;
import gov.usgs.cida.wqp.util.HttpUtils;
import gov.usgs.cida.wqp.util.MimeType;
import gov.usgs.cida.wqp.util.MybatisConstants;
import gov.usgs.cida.wqp.validation.ParameterValidation;
import gov.usgs.cida.wqp.validation.ValidationConstants;
import gov.usgs.cida.wqp.webservice.AsyncUtils;
import gov.usgs.cida.wqp.webservice.BaseController;
import gov.usgs.cida.wqp.webservice.HeaderWorker;
import gov.usgs.cida.wqp.webservice.StationColumnMapper;

import java.io.OutputStream;
import java.math.BigDecimal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.async.DeferredResult;

@Controller
public class SimpleStationController extends BaseController implements HttpConstants, MybatisConstants, ValidationConstants {
	private final Logger log = LoggerFactory.getLogger(getClass());

	protected IStreamingDao streamingDao;
	protected ICountDao countDao;
	protected IParameterHandler parameterHandler;
	protected ILogService logService;
	protected ParameterMap pm;

	
	@Autowired
	public SimpleStationController(IStreamingDao streamingDao, ICountDao countDao, 
			IParameterHandler parameterHandler, ILogService logService) {
		
		log.trace(getClass().getName());
		
		this.streamingDao     = streamingDao;
		this.parameterHandler = parameterHandler;
		this.countDao         = countDao;
		this.logService       = logService;
	}
	

	/**
	 * SimpleStation HEAD request
	 */
	@RequestMapping(value=SIMPLE_STATION_ENDPOINT, method=RequestMethod.HEAD, produces={MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE})
	public DeferredResult<String> simpleStationHeadRequest(HttpServletRequest request, HttpServletResponse response) {
		log.info("Processing Head: {}", request.getQueryString());
		BigDecimal logId = logService.logRequest(request, response);
		SCManager session = null;
		
		DeferredResult<String> deferral = new DeferredResult<String>(Time.HOUR.asMS());
		try {
			session = doHeaderOnly(request, response, logId, deferral);
		} finally {
			logService.logRequestComplete(logId, String.valueOf(response.getStatus()));
			Closer.close(session);
			log.info("Processing Head complete: {}", request.getQueryString());
		}
		return deferral;
	}
	
	
	private SCManager doHeaderOnly(HttpServletRequest request, HttpServletResponse response, BigDecimal logId, DeferredResult<String> deferral) {
		return doHeader(request, response, logId, deferral);
	}
	private SCManager doHeaderPlus(HttpServletRequest request, HttpServletResponse response, BigDecimal logId, DeferredResult<String> deferral) {
		DeferredResult<String> deferralProxy = new DeferredResult<String>(Time.HOUR.asMS());
		SCManager session = doHeader(request, response, logId, deferralProxy);
		if ("faulure".equals( deferralProxy.getResult() )) {
			deferral.setResult( (String) deferralProxy.getResult() );
		}
		return session;
	}
	
	
	/**
	 * Shared header helper method share for both the HEAD and GET requests
	 * @param request
	 * @param response
	 * @return cDAT session opened here for use on the GET request - bit kluggy but DRY'er code
	 */
	private SCManager doHeader(HttpServletRequest request, HttpServletResponse response, BigDecimal logId,  DeferredResult<String> deferral) {
		response.setCharacterEncoding(DEFAULT_ENCODING);
		pm = new ParameterValidation().preProcess(request, parameterHandler);
		if ( ! pm.isValid() ) {
			HttpUtils httpUtils = new HttpUtils();
			httpUtils.writeWarningHeaders(response, pm.getValidationMessages(), WQX_EMPTY_DOC);
			log.info("Processing Head invalid params end:{}", request.getQueryString());
			return null;
		}
		SCManager   session = SCManager.open().setAutoStart(true);
		HeaderWorker header = new HeaderWorker(response, ICountDao.SIMPLE_STATION_NAMESPACE, pm, countDao, MimeType.xml);
		String stationCount = session.addWorker("SimpleStationCount", header);

		AsyncUtils.waitForComplete(session, stationCount, deferral);

		if (header.hasError()) {
			//TODO We can't just eat these.
			throw new RuntimeException(header.getCurrentError());
		}
		logService.logHeadComplete(response, logId);
		return session;
	}
	
	
	/**
	 * station search request
	 */
	@RequestMapping(value=SIMPLE_STATION_ENDPOINT, method=RequestMethod.GET, produces={MIME_TYPE_XLSX, MIME_TYPE_XML, MIME_TYPE_JSON})
	public DeferredResult<String> stationGetRequest(HttpServletRequest request, HttpServletResponse response) {
		log.trace(""); // blank line during trace
		log.info("Processing Get: {}", request.getQueryString());
		BigDecimal logId = logService.logRequest(request, response);
		
		SCManager session = null;
		DeferredResult<String> deferral = new DeferredResult<String>(Time.HOUR.asMS());
		try {
			session = doHeaderPlus(request, response, logId, deferral);
			if (session != null) {
				
				Transformer transformer; 
				OutputStream responseStream = response.getOutputStream();
				String mimeTypeParam = pm.getParameter(Parameters.MIMETYPE);
				MimeType mimeType = MimeType.xml.fromString(mimeTypeParam);
				
				switch (mimeType) {
					case json:
						String header = "{\"type\":\"FeatureCollection\",\"features\":[";
						String footer = "]}";
						transformer = new MapToJsonTransformer(header, footer);
						transformer = new SimpleStationMapReformater(transformer);
						break;
					case xlsx:
						transformer = new MapToXlsxTransformer(responseStream, StationColumnMapper.mappings);
						break;
					case xml:
					default:
						IXmlMapping mapping = new SimpleStationXmlMapping();
						String xmlRootNode  = "<" + mapping.getRoot() + " " + mapping.getRootNamespace() + ">";
						transformer = new MapToXmlTransformer(mapping, xmlRootNode);
						break;
				}
				TransformOutputStream transformStream = new ObjectTransformStream(responseStream, logService, logId, transformer);
				StreamContainer<TransformOutputStream> transformProvider = new SimpleStreamContainer<TransformOutputStream>(transformStream);
				
				SimpleStationWorker worker = new SimpleStationWorker(IDao.SIMPLE_STATION_NAMESPACE, pm, streamingDao, transformProvider);
				String stationName = session.addWorker("SimpleStation", worker);

				AsyncUtils.listenForComplete(session, stationName, deferral, true);
			}
		} catch (Exception e) {
			//TODO We can't just eat these.
			log.error("Error openging outputstream",e);
			throw new RuntimeException(e);
		} finally {
			logService.logRequestComplete(logId, String.valueOf(response.getStatus()));
//			Closer.close(session); // handled in the AsyncUtils
			log.info("Processing Get complete: {}", request.getQueryString());
		}
		return deferral;
	}

}