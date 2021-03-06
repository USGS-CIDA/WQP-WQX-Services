package gov.usgs.wma.wqp.webservice.summary;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gov.usgs.wma.wqp.dao.intfc.ICountDao;
import gov.usgs.wma.wqp.dao.intfc.IStreamingDao;
import gov.usgs.wma.wqp.mapping.Profile;
import gov.usgs.wma.wqp.mapping.delimited.PeriodOfRecordDelimited;
import gov.usgs.wma.wqp.mapping.xml.IXmlMapping;
import gov.usgs.wma.wqp.openapi.ConfigOpenApi;
import gov.usgs.wma.wqp.openapi.annotation.FormUrlPostOperation;
import gov.usgs.wma.wqp.openapi.annotation.GetOperation;
import gov.usgs.wma.wqp.openapi.annotation.HeadOperation;
import gov.usgs.wma.wqp.openapi.annotation.PostCountOperation;
import gov.usgs.wma.wqp.openapi.annotation.PostOperation;
import gov.usgs.wma.wqp.openapi.annotation.post.SummaryParametersPostMonitoringLocation;
import gov.usgs.wma.wqp.openapi.annotation.query.MimeTypeCsvGeo;
import gov.usgs.wma.wqp.openapi.annotation.query.SummaryParameterListMonitoringLocation;
import gov.usgs.wma.wqp.openapi.annotation.query.Zip;
import gov.usgs.wma.wqp.openapi.model.StationCountJson;
import gov.usgs.wma.wqp.parameter.FilterParameters;
import gov.usgs.wma.wqp.service.ConfigurationService;
import gov.usgs.wma.wqp.service.ILogService;
import gov.usgs.wma.wqp.util.HttpConstants;
import gov.usgs.wma.wqp.webservice.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name=ConfigOpenApi.SUMMARY_MONITORING_LOCATION_TAG_NAME, description=ConfigOpenApi.TAG_DESCRIPTION)
@RestController
@RequestMapping(value=HttpConstants.SUMMARY_MONITORING_LOCATION_ENDPOINT,
	produces={HttpConstants.MIME_TYPE_GEOJSON,
			HttpConstants.MIME_TYPE_CSV})
public class SummaryMonitoringLocationController extends BaseController {

	protected final IXmlMapping xmlMapping;
	protected final IXmlMapping kmlMapping;

	@Autowired
	public SummaryMonitoringLocationController(
		IStreamingDao inStreamingDao, 
		ICountDao inCountDao, 
		ILogService inLogService,
		@Qualifier("stationWqx") IXmlMapping inXmlMapping,
		@Qualifier("stationKml") IXmlMapping inKmlMapping,
		ContentNegotiationStrategy contentStrategy,
		Validator validator, 
		ConfigurationService configurationService) 
	{
		super(inStreamingDao, inCountDao, inLogService, contentStrategy, validator, configurationService);
		xmlMapping = inXmlMapping;
		kmlMapping = inKmlMapping;
	}

	@Operation(description=HeadOperation.DEFAULT_DESCRIPTION)
	@SummaryParameterListMonitoringLocation
	@RequestMapping(method=RequestMethod.HEAD)
	public void summaryMonitoringLocationHeadRequest(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden=true) FilterParameters filter
			) {
		doHeadRequest(request, response, filter);
	}

	@Operation(description=GetOperation.DEFAULT_DESCRIPTION)
	@SummaryParameterListMonitoringLocation
	@RequestMapping(method=RequestMethod.GET)
	public void summaryMonitoringLocationGetRequest(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden=true) FilterParameters filter
			) {
		doDataRequest(request, response, filter);
	}

	@Operation(description=PostOperation.DEFAULT_DESCRIPTION)
	@MimeTypeCsvGeo
	@Zip
	@SummaryParametersPostMonitoringLocation
	@PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
	public void summaryMonitoringLocationJsonPostRequest(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden=true) @RequestParam(value="mimeType", required=false) String mimeType,
			@Parameter(hidden=true) @RequestParam(value="zip", required=false) String zip,
			@Parameter(hidden=true) @RequestBody FilterParameters filter
			) {
		doDataRequest(request, response, filter, mimeType, zip);
	}

	@FormUrlPostOperation
	public void summaryMonitoringLocationFormUrlencodedPostRequest(HttpServletRequest request, HttpServletResponse response, FilterParameters filter) {
		doDataRequest(request, response, filter);
	}

	@PostCountOperation
	@MimeTypeCsvGeo
	@Zip
	@SummaryParametersPostMonitoringLocation
	@ApiResponse(
			responseCode="200",
			description="OK",
			content=@Content(schema=@Schema(implementation=StationCountJson.class)))
	public Map<String, String> summaryMonitoringLocationPostCountRequest(
			HttpServletRequest request,
			HttpServletResponse response,
			@Parameter(hidden=true) @RequestParam(value="mimeType", required=false) String mimeType,
			@Parameter(hidden=true) @RequestParam(value="zip", required=false) String zip,
			@Parameter(hidden=true) @RequestBody FilterParameters filter
			) {
		return doPostCountRequest(request, response, filter, mimeType, zip);
	}

	@Override
	protected String addCountHeaders(HttpServletResponse response, List<Map<String, Object>> counts) {
		addSiteHeaders(response, counts);
		return HttpConstants.HEADER_TOTAL_SITE_COUNT;
	}

	@Override
	protected Map<String, String> getMapping(Profile profile) {
		return PeriodOfRecordDelimited.getMapping(profile);
	}

	@Override
	protected IXmlMapping getXmlMapping() {
		return null;
	}

	@Override
	protected IXmlMapping getKmlMapping() {
		return null;
	}

	@Override
	protected Profile determineProfile(FilterParameters filter) {
		return determineProfile(Profile.SUMMARY_MONITORING_LOCATION, filter);
	}

	@Override
	protected void addCustomRequestParams() {
		getFilter().setSiteUrlBase(configurationService.getMyUrlBase());
	}

}
