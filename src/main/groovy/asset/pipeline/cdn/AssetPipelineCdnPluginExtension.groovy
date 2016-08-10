package asset.pipeline.cdn

class AssetPipelineCdnPluginExtension extends AssetProviderConfig {

    /**
     * Date or number of days.
     */
    def expires

    String localStoragePath = 'assets/'
    List providers = []

    /**
     * Boolean or 'both'.
     */
    def gzip = ''
}
