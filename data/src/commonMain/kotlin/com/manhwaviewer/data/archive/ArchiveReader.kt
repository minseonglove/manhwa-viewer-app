package com.manhwaviewer.data.archive

expect class ArchiveReader() {
    /**
     * 아카이브(ZIP/CBZ) 또는 폴더 내 이미지 파일 이름 목록을 정렬하여 반환
     */
    fun getImageEntries(archivePath: String): List<String>

    /**
     * 정렬 기준을 적용하여 이미지 파일 이름 목록을 반환
     * @param sortByDate true이면 날짜순, false이면 이름순
     * @param ascending true이면 오름차순, false이면 내림차순
     */
    fun getImageEntriesSorted(archivePath: String, sortByDate: Boolean, ascending: Boolean): List<String>

    /**
     * 아카이브에서 특정 이미지를 바이트 배열로 반환 (압축 해제 없이 스트리밍)
     */
    fun getImageBytes(archivePath: String, entryName: String): ByteArray?

    /**
     * 아카이브 내 총 이미지 페이지 수
     */
    fun getPageCount(archivePath: String): Int

    /**
     * 리소스 해제
     */
    fun close()
}
